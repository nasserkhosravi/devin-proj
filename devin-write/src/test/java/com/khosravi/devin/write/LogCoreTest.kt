package com.khosravi.devin.write

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.write.api.DevinLogCore
import com.khosravi.devin.write.testing.DevinTestFixture
import com.khosravi.devin.write.testing.DevinTestFixture.onWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogCoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        DevinTestFixture.resetSingletons()
        DevinTestFixture.registerProvider()
    }

    @After
    fun tearDown() {
        DevinTestFixture.resetSingletons()
    }

    @Test
    fun `insertLog writes under this app's package`() {
        val core = LogCore(context, isEnable = true)

        val uri = onWorker { core.insertLog("tag", "value", "type", meta = "{}", metaIndex = "url" to "x") }!!

        val row = DevinTestFixture.logs(context).single()
        assertEquals(context.packageName, row.clientId)
        assertEquals("tag", row.tag)
        assertEquals("value", row.value)
        assertEquals("type", row.typeId)
        assertEquals("{}", row.meta)
        assertEquals(listOf("url" to "x"), DevinTestFixture.metaIndexes(context, uri.lastPathSegment!!.toLong()))
    }

    @Test
    fun `null or empty tag becomes untag`() {
        val core = LogCore(context, isEnable = true)

        onWorker {
            core.insertLog(null, "a", null)
            core.insertLog("", "b", null)
        }

        assertEquals(setOf(LoggerImpl.LOG_TAG_UNTAG), DevinTestFixture.logs(context).map { it.tag }.toSet())
    }

    @Test
    fun `updateLog rewrites an existing log`() {
        val core = LogCore(context, isEnable = true)
        val uri = onWorker { core.insertLog("tag", "old", "type") }!!

        val result = onWorker { core.updateLog(uri, null, "new", "type2", meta = "{}") }

        assertEquals(DevinLogCore.FLAG_OPERATION_SUCCESS, result)
        val row = DevinTestFixture.logs(context).single()
        assertEquals(LoggerImpl.LOG_TAG_UNTAG, row.tag)
        assertEquals("new", row.value)
        assertEquals("type2", row.typeId)
    }

    @Test
    fun `disabled core writes nothing`() {
        val enabled = LogCore(context, isEnable = true)
        val uri = onWorker { enabled.insertLog("tag", "kept", null) }!!
        val disabled = LogCore(context, isEnable = false)

        assertNull(onWorker { disabled.insertLog("tag", "dropped", null) })
        assertEquals(DevinLogCore.FLAG_OPERATION_FAILED, onWorker { disabled.updateLog(uri, "tag", "changed", "type") })
        assertEquals(listOf("kept"), DevinTestFixture.logs(context).map { it.value })
    }
}
