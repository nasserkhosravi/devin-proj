package com.khosravi.devin.write

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.read.DevinLogFlagsApi
import com.khosravi.devin.write.testing.DevinTestFixture
import com.khosravi.devin.write.testing.DevinTestFixture.onWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevinExceptionLoggerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exceptionLogger = DevinExceptionLogger(LogCore(context, isEnable = true))

    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private val previousCalls = mutableListOf<Throwable>()
    private val previousHandler = Thread.UncaughtExceptionHandler { _, e -> previousCalls.add(e) }

    @Before
    fun setUp() {
        DevinTestFixture.resetSingletons()
        DevinTestFixture.registerProvider()
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        DevinTestFixture.resetSingletons()
    }

    private fun crash(e: Throwable) = onWorker {
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), e)
    }

    @Test
    fun `enabled handler logs the crash then calls the previous handler`() {
        exceptionLogger.generalUncaughtExceptionLogging(true)
        val boom = IllegalStateException("boom")

        crash(boom)

        val row = DevinTestFixture.logs(context).single()
        assertEquals(DevinLogFlagsApi.TAG_UNCAUGHT_EXCEPTION, row.tag)
        assertEquals(DevinExceptionLogger.TYPE_ID, row.typeId)
        assertEquals("General uncaught exception", row.value)
        val meta = row.metaJson()
        assertEquals(DevinExceptionLogger.VALUE_ERROR_REPORT_META_TYPE, meta.getString(DevinLogFlagsApi.KEY_META_TYPE))
        assertEquals(Log.ERROR, meta.getInt(DevinLogFlagsApi.KEY_LOG_LEVEL))
        assertTrue(meta.getString(DevinLogFlagsApi.KEY_LOG_THROWABLE).contains("IllegalStateException: boom"))
        assertEquals(listOf<Throwable>(boom), previousCalls)
    }

    @Test
    fun `disabling restores the previous handler`() {
        exceptionLogger.generalUncaughtExceptionLogging(true)

        exceptionLogger.generalUncaughtExceptionLogging(false)

        assertSame(previousHandler, Thread.getDefaultUncaughtExceptionHandler())
    }

    @Test
    fun `disabling without enabling keeps the current handler`() {
        exceptionLogger.generalUncaughtExceptionLogging(false)

        assertSame(previousHandler, Thread.getDefaultUncaughtExceptionHandler())
    }

    @Test
    fun `enabling twice logs each crash once`() {
        exceptionLogger.generalUncaughtExceptionLogging(true)
        exceptionLogger.generalUncaughtExceptionLogging(true)

        crash(RuntimeException("boom"))

        assertEquals(1, DevinTestFixture.logs(context).size)
        assertEquals(1, previousCalls.size)
    }
}
