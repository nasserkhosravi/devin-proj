package com.khosravi.devin.write

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.api.DevinLogger
import com.khosravi.devin.read.DevinLogFlagsApi
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.testing.DevinTestFixture
import com.khosravi.devin.write.testing.DevinTestFixture.WRITE_TIMEOUT_MS
import com.khosravi.devin.write.testing.DevinTestFixture.onWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoggerImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logCore = LogCore(context, isEnable = true)
    private val logger = LoggerImpl(logCore)

    @Before
    fun setUp() {
        DevinTestFixture.resetSingletons()
        DevinTestFixture.registerProvider()
    }

    @After
    fun tearDown() {
        DevinTestFixture.resetSingletons()
    }

    /** Logs are written in batches off the caller's thread, so wait for them before reading. */
    private fun singleLog(): DevinTestFixture.LogRow {
        logCore.awaitPendingWrites(WRITE_TIMEOUT_MS)
        return DevinTestFixture.logs(context).single()
    }

    private fun clearLogs() = onWorker {
        context.contentResolver.delete(DevinUriHelper.getLogListUri(context.packageName), null, null)
    }

    @Test
    fun `each level method records its log level`() {
        val calls: List<Pair<Int, DevinLogger.() -> Unit>> = listOf(
            Log.DEBUG to { debug("t", "m") },
            Log.INFO to { info("t", "m") },
            Log.WARN to { warning("t", "m") },
            Log.ERROR to { error("t", "m") },
            Log.VERBOSE to { verbose("t", "m", null) },
            Log.ASSERT to { send(Log.ASSERT, "t", "m", null) },
        )

        for ((level, call) in calls) {
            onWorker { logger.call() }
            val meta = singleLog().metaJson()
            assertEquals(level, meta.getInt(DevinLogFlagsApi.KEY_LOG_LEVEL))
            assertEquals(LoggerImpl.VALUE_LOG_META_TYPE, meta.getString(LoggerImpl.KEY_META_TYPE))
            clearLogs()
        }
    }

    @Test
    fun `user log stores tag, message and no type`() {
        onWorker { logger.info("tag", "message") }

        val row = singleLog()
        assertEquals("tag", row.tag)
        assertEquals("message", row.value)
        assertEquals(null, row.typeId)
    }

    @Test
    fun `null tag becomes untag`() {
        onWorker { logger.info(null, "message") }

        assertEquals(LoggerImpl.LOG_TAG_UNTAG, singleLog().tag)
    }

    @Test
    fun `payload and throwable are only added when given`() {
        onWorker { logger.error("t", "plain") }
        val plain = singleLog().metaJson()
        assertFalse(plain.has(LoggerImpl.KEY_LOG_PAYLOAD))
        assertFalse(plain.has(LoggerImpl.KEY_LOG_THROWABLE))
        clearLogs()

        onWorker { logger.error("t", "full", payload = "{\"a\":1}", throwable = IllegalStateException("boom")) }
        val full = singleLog().metaJson()
        assertEquals("{\"a\":1}", full.getString(LoggerImpl.KEY_LOG_PAYLOAD))
        assertTrue(full.getString(LoggerImpl.KEY_LOG_THROWABLE).contains("IllegalStateException: boom"))
    }

    @Test
    fun `doIfEnable runs the action with this logger when enabled`() {
        var received: DevinLogger? = null

        logger.doIfEnable { received = it }

        assertSame(logger, received)
    }

    @Test
    fun `disabled logger writes nothing and skips doIfEnable`() {
        val disabledCore = LogCore(context, isEnable = false)
        val disabled = LoggerImpl(disabledCore)
        var called = false

        onWorker {
            disabled.info("t", "m")
            disabled.error("t", "m", throwable = RuntimeException())
            disabled.logCallerFunc()
            disabled.doIfEnable { called = true }
        }

        assertFalse(called)
        disabledCore.awaitPendingWrites(WRITE_TIMEOUT_MS)
        assertEquals(0, DevinTestFixture.logs(context).size)
    }

    @Test
    fun `logCallerFunc writes a trace log with method and parent`() {
        onWorker { logger.logCallerFunc() }

        val row = singleLog()
        assertEquals(LoggerImpl.LOG_TAG_FUNC_TRACE, row.tag)
        val meta = row.metaJson()
        assertEquals(LoggerImpl.LOG_TAG_FUNC_TRACE, meta.getString(LoggerImpl.KEY_META_TYPE))
        val methodName = meta.getString("method_name")
        val parentName = meta.getString("parent_name")
        assertTrue(meta.getBoolean("is_class_name"))
        // Value is the simple class name plus the method; which frame is picked depends on the
        // runtime's stack layout (ART adds a VMStack frame the JVM doesn't), so only the shape is checked.
        assertEquals("${parentName.substringAfterLast(".")} $methodName", row.value)
    }

    @Test
    fun `logSessionStart uses the given message and payload`() {
        onWorker { logger.logSessionStart(context, message = "hello", payload = "details") }

        val row = singleLog()
        assertEquals(DevinLogFlagsApi.TAG_SESSION_START, row.tag)
        assertEquals("hello", row.value)
        assertEquals("details", row.metaJson().getString(LoggerImpl.KEY_LOG_PAYLOAD))
        assertEquals(Log.INFO, row.metaJson().getInt(DevinLogFlagsApi.KEY_LOG_LEVEL))
    }

    @Test
    fun `logSessionStart builds a default message and device payload`() {
        onWorker { logger.logSessionStart(context) }

        val row = singleLog()
        assertEquals(DevinLogFlagsApi.TAG_SESSION_START, row.tag)
        assertTrue(row.value.startsWith("----Session Started:"))
        val payload = row.metaJson().getString(LoggerImpl.KEY_LOG_PAYLOAD)
        listOf("Timestamp:", "App Version:", "Android:", "Device:", "Screen:").forEach {
            assertTrue("payload misses $it", payload.contains(it))
        }
    }
}
