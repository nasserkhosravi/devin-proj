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
        logCore.insertLog(tag = LOG_TAG, "General uncaught exception", typeId = TYPE_ID, meta = meta.toString())
    }

    companion object {
        //error-report
        const val LOG_TAG = "uncaught_exception"
        const val TYPE_ID = "exception"
        const val VALUE_ERROR_REPORT_META_TYPE = "general_uncaught_exception"
    }
}