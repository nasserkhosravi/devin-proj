package com.khosravi.devin.write

import android.util.Log
import com.khosravi.devin.read.DevinLogFlagsApi
import com.khosravi.devin.write.exception.TwinUncaughtExceptionHandler

internal class DevinExceptionLogger(
    private val logCore: LogCore
) {

    private var uncaughtExceptionHandler: TwinUncaughtExceptionHandler? = null

    fun generalUncaughtExceptionLogging(isEnable: Boolean) {
        val current = uncaughtExceptionHandler
        if (!isEnable) {
            // Never installed: leave the app's handler alone instead of replacing it with null.
            if (current == null) return
            Thread.setDefaultUncaughtExceptionHandler(current.defaultUncaughtExceptionHandler)
            uncaughtExceptionHandler = null
            return
        }
        // Already installed: wrapping again would log every crash twice.
        if (current != null) return
        uncaughtExceptionHandler = TwinUncaughtExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler)
    }

    fun exceptionHappened(e: Throwable) {
        // Let queued logs land first, so they appear before the crash. The crash log itself stays synchronous
        // because the process dies right after this returns.
        logCore.awaitPendingWrites(PENDING_LOGS_FLUSH_TIMEOUT_MS)
        val meta = LoggerImpl.createMetaForComponentLogs(VALUE_ERROR_REPORT_META_TYPE, Log.ERROR, null, e)
        logCore.insertLog(tag = DevinLogFlagsApi.TAG_UNCAUGHT_EXCEPTION, "General uncaught exception", typeId = TYPE_ID, meta = meta.toString())
    }

    companion object {
        //error-report
        const val TYPE_ID = "exception"
        private const val PENDING_LOGS_FLUSH_TIMEOUT_MS = 500L
        const val VALUE_ERROR_REPORT_META_TYPE = "general_uncaught_exception"
    }
}