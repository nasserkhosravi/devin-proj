package com.khosravi.devin.write

import android.util.Log
import com.khosravi.devin.write.exception.TwinUncaughtExceptionHandler

internal class DevinExceptionLogger(
    private val logCore: LogCore
) {

    private var uncaughtExceptionHandler: TwinUncaughtExceptionHandler? = null

    fun generalUncaughtExceptionLogging(isEnable: Boolean) {
        if (!isEnable) {
            Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler?.defaultUncaughtExceptionHandler)
            uncaughtExceptionHandler = null
            return
        }
        uncaughtExceptionHandler = TwinUncaughtExceptionHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler)
    }

    fun exceptionHappened(e: Throwable) {
        val meta = LoggerImpl.createMetaForComponentLogs(VALUE_ERROR_REPORT_META_TYPE, Log.ERROR, null, e)
        logCore.insertLog(ERROR_REPORT_TAG, "General uncaught exception", meta.toString())
    }

    companion object {
        //error-report
        const val ERROR_REPORT_TAG = "exception_report"
        const val VALUE_ERROR_REPORT_META_TYPE = "general_uncaught_exception"
    }
}