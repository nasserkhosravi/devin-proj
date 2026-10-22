package com.khosravi.devin.write

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.api.DevinLogger
import com.khosravi.devin.read.DevinLogFlagsApi
import com.khosravi.devin.write.testing.DevinTestFixture
import com.khosravi.devin.write.testing.DevinTestFixture.WRITE_TIMEOUT_MS
import com.khosravi.devin.write.testing.DevinTestFixture.onWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The `meta` overloads of [DevinLogger]: structured fields stored under `fields` in the log's meta. */
@RunWith(RobolectricTestRunner::class)
class LoggerFieldsTest {

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

    private fun singleLog(): DevinTestFixture.LogRow {
        logCore.awaitPendingWrites(WRITE_TIMEOUT_MS)
        return DevinTestFixture.logs(context).single()
    }

    @Test
    fun `fields are stored under fields next to the usual meta`() {
        onWorker {
            logger.error("payment", "Payment failed", meta = mapOf("orderId" to "8812", "retryCount" to 3), payload = "raw")
        }

        val row = singleLog()
        val meta = row.metaJson()
        assertEquals("payment", row.tag)
        assertEquals("Payment failed", row.value)
        assertEquals(Log.ERROR, meta.getInt(DevinLogFlagsApi.KEY_LOG_LEVEL))
        assertEquals("raw", meta.getString(LoggerImpl.KEY_LOG_PAYLOAD))
        val fields = meta.getJSONObject(DevinLogFlagsApi.KEY_LOG_FIELDS)
        assertEquals("8812", fields.getString("orderId"))
        assertEquals(3, fields.getInt("retryCount"))
    }

    @Test
    fun `each meta overload records its level`() {
        val calls: List<Pair<Int, DevinLogger.() -> Unit>> = listOf(
            Log.DEBUG to { debug("t", "m", meta = mapOf("k" to 1)) },
            Log.INFO to { info("t", "m", meta = mapOf("k" to 1)) },
            Log.WARN to { warning("t", "m", meta = mapOf("k" to 1)) },
            Log.ERROR to { error("t", "m", meta = mapOf("k" to 1)) },
            Log.VERBOSE to { verbose("t", "m", meta = mapOf("k" to 1)) },
            Log.ASSERT to { send(Log.ASSERT, "t", "m", meta = mapOf("k" to 1)) },
        )
        for ((level, call) in calls) {
            onWorker { logger.call() }
            val meta = singleLog().metaJson()
            assertEquals(level, meta.getInt(DevinLogFlagsApi.KEY_LOG_LEVEL))
            assertEquals(1, meta.getJSONObject(DevinLogFlagsApi.KEY_LOG_FIELDS).getInt("k"))
            onWorker { context.contentResolver.delete(com.khosravi.devin.read.DevinUriHelper.getLogListUri(context.packageName), null, null) }
        }
    }

    @Test
    fun `empty fields and logs without fields store no fields key`() {
        onWorker { logger.info("t", "empty", meta = emptyMap()) }
        assertFalse(singleLog().metaJson().has(DevinLogFlagsApi.KEY_LOG_FIELDS))
        onWorker { context.contentResolver.delete(com.khosravi.devin.read.DevinUriHelper.getLogListUri(context.packageName), null, null) }

        onWorker { logger.info("t", "plain") }
        assertFalse(singleLog().metaJson().has(DevinLogFlagsApi.KEY_LOG_FIELDS))
    }

    @Test
    fun `a disabled logger writes nothing for meta overloads`() {
        val disabledCore = LogCore(context, isEnable = false)
        onWorker { LoggerImpl(disabledCore).error("t", "m", meta = mapOf("k" to 1)) }
        disabledCore.awaitPendingWrites(WRITE_TIMEOUT_MS)
        assertEquals(0, DevinTestFixture.logs(context).size)
    }
}
