package com.khosravi.devin.present.retention

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.testing.DevinDbFixture
import com.khosravi.devin.present.testing.DevinDbFixture.insertClient
import com.khosravi.devin.present.testing.DevinDbFixture.insertLog
import com.khosravi.devin.present.testing.DevinDbFixture.logCount
import com.khosravi.devin.present.testing.DevinDbFixture.logDates
import com.khosravi.devin.write.room.DevinDB
import com.khosravi.devin.write.room.MetaIndexTable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class RetentionCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var room: DevinDB
    private lateinit var db: SupportSQLiteDatabase
    private lateinit var filesDir: File
    private lateinit var cleaner: RetentionCleaner

    private val now = TimeUnit.DAYS.toMillis(1000)
    private val day = TimeUnit.DAYS.toMillis(1)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        room = Room.inMemoryDatabaseBuilder(context, DevinDB::class.java).allowMainThreadQueries().build()
        db = room.openHelper.writableDatabase
        filesDir = tempFolder.newFolder("files")
        cleaner = RetentionCleaner(db, filesDir) { now }
    }

    @After
    fun tearDown() {
        room.close()
    }

    private fun policy(maxAgeDays: Int = 7, maxCount: Int = 10_000) = RetentionPolicy(true, maxAgeDays, maxCount)

    private fun trim(policy: RetentionPolicy = policy(), lastVacuumAt: Long = now) = cleaner.trim(policy, lastVacuumAt)

    @Test
    fun `deletes logs older than max age and keeps newer ones`() {
        insertLog(db, "a", now - 8 * day)
        insertLog(db, "a", now - 30 * day)
        insertLog(db, "b", now - 10 * day)
        insertLog(db, "a", now - 6 * day)
        insertLog(db, "b", now)

        val result = trim(policy(maxAgeDays = 7))

        assertEquals(3, result.deletedRows)
        assertEquals(listOf(now - 6 * day), logDates(db, "a"))
        assertEquals(listOf(now), logDates(db, "b"))
    }

    @Test
    fun `log exactly at the age cutoff is kept`() {
        insertLog(db, "a", now - 7 * day)
        assertEquals(0, trim(policy(maxAgeDays = 7)).deletedRows)
        assertEquals(1, logCount(db))
    }

    @Test
    fun `keeps only the newest logs per client`() {
        (1..5).forEach { insertLog(db, "a", now - it) }
        (1..2).forEach { insertLog(db, "b", now - it) }

        val result = trim(policy(maxCount = 3))

        assertEquals(2, result.deletedRows)
        assertEquals(listOf(now - 3, now - 2, now - 1), logDates(db, "a"))
        assertEquals(2, logCount(db, "b"))
    }

    @Test
    fun `count cap orders by date, not insertion order`() {
        insertLog(db, "a", now - 1)
        insertLog(db, "a", now - 100)
        insertLog(db, "a", now - 50)

        trim(policy(maxCount = 2))

        assertEquals(listOf(now - 50, now - 1), logDates(db, "a"))
    }

    @Test
    fun `count cap deletes excess larger than one batch`() {
        DevinDbFixture.inTransaction(db) {
            (1..12_345).forEach { insertLog(db, "a", now - it) }
        }

        val result = trim(policy(maxCount = 1_000))

        assertEquals(11_345, result.deletedRows)
        val remaining = logDates(db, "a")
        assertEquals(1_000, remaining.size)
        assertEquals(now - 1_000, remaining.first())
        assertEquals(now - 1, remaining.last())
    }

    @Test
    fun `age delete larger than one batch removes everything expired`() {
        DevinDbFixture.inTransaction(db) {
            (1..7_000).forEach { insertLog(db, "a", now - 30 * day - it) }
            insertLog(db, "a", now)
        }

        assertEquals(7_000, trim().deletedRows)
        assertEquals(listOf(now), logDates(db, "a"))
    }

    @Test
    fun `age and count rules combine`() {
        insertLog(db, "a", now - 10 * day)
        (1..4).forEach { insertLog(db, "a", now - it) }

        val result = trim(policy(maxAgeDays = 7, maxCount = 2))

        assertEquals(3, result.deletedRows)
        assertEquals(listOf(now - 2, now - 1), logDates(db, "a"))
    }

    @Test
    fun `nothing to delete reports zero`() {
        insertLog(db, "a", now)
        val result = trim()
        assertEquals(0, result.deletedRows)
        assertEquals(0, result.deletedContentDirs)
    }

    @Test
    fun `meta index rows of deleted logs are removed`() {
        val oldId = insertLog(db, "a", now - 30 * day)
        val newId = insertLog(db, "a", now)
        DevinDbFixture.insertMetaIndex(db, oldId, "url", "https://old")
        DevinDbFixture.insertMetaIndex(db, newId, "url", "https://new")

        trim()

        assertEquals(1, DevinDbFixture.count(db, "SELECT COUNT(*) FROM ${MetaIndexTable.TABLE_NAME}"))
        assertEquals(
            newId,
            DevinDbFixture.count(db, "SELECT ${MetaIndexTable.COLUMN_ID} FROM ${MetaIndexTable.TABLE_NAME}"),
        )
    }

    @Test
    fun `sweeps content dirs of missing logs for registered clients only`() {
        insertClient(db, "a")
        val keptId = insertLog(db, "a", now)
        val keptDir = contentDir("a", keptId.toString())
        val orphanDir = contentDir("a", "999")
        val zeroDir = contentDir("a", "0")
        val notALogDir = contentDir("a", "cache")
        val unregisteredClientDir = contentDir("b", "999")

        val result = trim()

        assertEquals(2, result.deletedContentDirs)
        assertTrue(keptDir.exists())
        assertFalse(orphanDir.exists())
        assertFalse(zeroDir.exists())
        assertTrue(notALogDir.exists())
        assertTrue(unregisteredClientDir.exists())
    }

    @Test
    fun `sweeps content of logs deleted in the same trim`() {
        insertClient(db, "a")
        val oldId = insertLog(db, "a", now - 30 * day)
        val oldDir = contentDir("a", oldId.toString())

        val result = trim()

        assertEquals(1, result.deletedRows)
        assertEquals(1, result.deletedContentDirs)
        assertFalse(oldDir.exists())
    }

    @Test
    fun `registered client without a content dir is fine`() {
        insertClient(db, "a")
        assertEquals(0, trim().deletedContentDirs)
    }

    @Test
    fun `does not vacuum within 24 hours of the last vacuum`() {
        assertFalse(trim(lastVacuumAt = now - TimeUnit.HOURS.toMillis(23)).vacuumed)
    }

    @Test
    fun `does not vacuum a database with little free space`() {
        insertLog(db, "a", now)
        assertFalse(trim(lastVacuumAt = 0L).vacuumed)
    }

    private fun contentDir(clientId: String, logId: String): File =
        File(filesDir, "$clientId/$logId").apply {
            mkdirs()
            File(this, "file_content").writeText("x")
        }
}
