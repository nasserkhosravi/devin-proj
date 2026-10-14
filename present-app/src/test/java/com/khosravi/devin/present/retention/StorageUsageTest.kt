package com.khosravi.devin.present.retention

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.testing.DevinDbFixture
import com.khosravi.devin.present.testing.ProviderFixture
import com.khosravi.devin.present.testing.ProviderFixture.onWorker
import com.khosravi.devin.write.room.DevinDB
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StorageUsageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ProviderFixture.register()
    }

    @After
    fun tearDown() {
        DevinDbFixture.resetSingleton()
    }

    private fun dbBytes(): Long {
        val path = context.getDatabasePath(DevinDB.getInstance(context).openHelper.databaseName!!).path
        onWorker { DevinDB.getInstance(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint").close() }
        return listOf("", "-wal", "-shm").sumOf { File(path + it).length() }
    }

    private fun writeContent(clientId: String, logId: String, bytes: Int) {
        File(context.filesDir, "$clientId/$logId").apply { mkdirs() }
            .resolve("file_content").writeBytes(ByteArray(bytes))
    }

    @Test
    fun `counts the database files when there is no content`() {
        ProviderFixture.putClient(context, "a")
        assertEquals(dbBytes(), onWorker { StorageUsage.computeBytes(context) })
    }

    @Test
    fun `adds content files of registered clients only`() {
        ProviderFixture.putClient(context, "a")
        ProviderFixture.putClient(context, "b")
        writeContent("a", "1", 1_000)
        writeContent("a", "2", 500)
        writeContent("b", "3", 250)
        writeContent("unregistered", "4", 10_000)
        File(context.filesDir, "other.txt").writeBytes(ByteArray(7_000))

        assertEquals(dbBytes() + 1_750, onWorker { StorageUsage.computeBytes(context) })
    }
}
