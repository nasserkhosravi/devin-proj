package com.khosravi.devin.api

interface DevinLogger {

    fun doIfEnable(action: (logger: DevinLogger) -> Unit)

    fun debug(tag: String?, message: String, payload: String? = null, throwable: Throwable? = null)
    fun info(tag: String?, message: String, payload: String? = null, throwable: Throwable? = null)
    fun warning(tag: String?, message: String, payload: String? = null, throwable: Throwable? = null)
    fun error(tag: String?, message: String, payload: String? = null, throwable: Throwable? = null)

    fun logCallerFunc()

    /**
     * Enable or disable general exception logging.
     * It use [Thread.setDefaultUncaughtExceptionHandler] to catch exception to log them.
     *
     * When this function called still previous [Thread.getDefaultUncaughtExceptionHandler] should be called.
     * See [com.khosravi.devin.write.DevinExceptionLogger]
     */
    fun generalUncaughtExceptionLogging(isEnable: Boolean)
}

