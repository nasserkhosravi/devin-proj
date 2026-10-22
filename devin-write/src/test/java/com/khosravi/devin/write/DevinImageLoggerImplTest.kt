package com.khosravi.devin.write

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.read.DevinImageFlagsApi
import com.khosravi.devin.read.DevinLogFlagsApi
import com.khosravi.devin.write.testing.DevinTestFixture
import com.khosravi.devin.write.testing.DevinTestFixture.WRITE_TIMEOUT_MS
import com.khosravi.devin.write.testing.DevinTestFixture.onWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevinImageLoggerImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logCore = LogCore(context, isEnable = true)
    private val imageLogger = DevinImageLoggerImpl(logCore)

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

    @Test
    fun `downloading without a name uses the url path`() {
        onWorker { imageLogger.downloading("https://cdn.example.com/a/b.png?size=2") }

        val row = singleLog()
        assertEquals(DevinImageFlagsApi.LOG_TAG, row.tag)
        assertEquals(DevinImageFlagsApi.TYPE_ID, row.typeId)
        assertEquals("Downloading /a/b.png", row.value)
        val meta = row.metaJson()
        assertEquals(DevinImageFlagsApi.VALUE_IMAGE_META_TYPE, meta.getString(DevinLogFlagsApi.KEY_META_TYPE))
        assertEquals("https://cdn.example.com/a/b.png?size=2", meta.getString(DevinImageFlagsApi.KEY_IMAGE_URL))
        assertEquals(DevinImageFlagsApi.Status.DOWNLOADING, meta.getInt(DevinImageFlagsApi.KEY_IMAGE_STATUS))
        assertEquals(Log.INFO, meta.getInt(DevinLogFlagsApi.KEY_LOG_LEVEL))
    }

    @Test
    fun `succeed with a name uses the name`() {
        onWorker { imageLogger.succeed("https://cdn.example.com/a.png", name = "avatar", payload = "p") }

        val row = singleLog()
        assertEquals("Succeed avatar", row.value)
        val meta = row.metaJson()
        assertEquals(DevinImageFlagsApi.Status.SUCCEED, meta.getInt(DevinImageFlagsApi.KEY_IMAGE_STATUS))
        assertEquals("p", meta.getString(LoggerImpl.KEY_LOG_PAYLOAD))
    }

    @Test
    fun `an empty name falls back to the url path`() {
        onWorker { imageLogger.succeed("https://cdn.example.com/a.png", name = "") }

        assertEquals("Succeed /a.png", singleLog().value)
    }

    @Test
    fun `failed is logged as an error with the throwable`() {
        onWorker { imageLogger.failed("https://cdn.example.com/a.png", throwable = RuntimeException("404")) }

        val row = singleLog()
        assertEquals("Failed /a.png", row.value)
        val meta = row.metaJson()
        assertEquals(DevinImageFlagsApi.Status.FAILED, meta.getInt(DevinImageFlagsApi.KEY_IMAGE_STATUS))
        assertEquals(Log.ERROR, meta.getInt(DevinLogFlagsApi.KEY_LOG_LEVEL))
        assertTrue(meta.getString(DevinLogFlagsApi.KEY_LOG_THROWABLE).contains("RuntimeException: 404"))
    }

    @Test
    fun `payload and throwable are left out when not given`() {
        onWorker { imageLogger.downloading("https://cdn.example.com/a.png") }

        val meta = singleLog().metaJson()
        assertFalse(meta.has(LoggerImpl.KEY_LOG_PAYLOAD))
        assertFalse(meta.has(DevinLogFlagsApi.KEY_LOG_THROWABLE))
    }

    @Test
    fun `disabled image logger writes nothing`() {
        val disabledCore = LogCore(context, isEnable = false)
        val disabled = DevinImageLoggerImpl(disabledCore)

        onWorker {
            disabled.downloading("https://cdn.example.com/a.png")
            disabled.failed("https://cdn.example.com/a.png")
            disabled.succeed("https://cdn.example.com/a.png")
        }

        disabledCore.awaitPendingWrites(WRITE_TIMEOUT_MS)
        assertEquals(0, DevinTestFixture.logs(context).size)
    }
}
