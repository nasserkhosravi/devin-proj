package com.khosravi.devin.api

import android.content.Context

interface DevinLogger {

    fun doIfEnable(action: (logger: DevinLogger) -> Unit)

    fun debug(tag: String?, message: String, payload: String? = null, throwable: Throwable? = null)
    fun info(tag: String?, message: String, payload: String? = null, throwable: Throwable? = null)
    fun warning(tag: String?, message: String, payload: String? = null, throwable: Throwable? = null)
    fun error(tag: String?, message: String, payload: String? = null, throwable: Throwable? = null)
    fun verbose(tag: String?, message: String, payload: String?, throwable: Throwable? = null)
    fun send(logLevel: Int, tag: String?, message: String, payload: String?, throwable: Throwable? = null)

    /**
     * Logs with structured fields. [message] says what happened; [meta] holds the facts as named values,
     * e.g. `mapOf("orderId" to "8812", "errorCode" to "E42", "retryCount" to 3)`, so people, tools and AI agents
     * can read them without parsing the message.
     *
     * Values can be String, Number, Boolean or null; anything else is stored with `toString()`. Up to 50 fields
     * are kept, keys are cut at 100 characters and string values at 2,000. Don't put secrets in fields.
     */
    fun debug(tag: String?, message: String, meta: Map<String, Any?>, payload: String? = null, throwable: Throwable? = null)
    fun info(tag: String?, message: String, meta: Map<String, Any?>, payload: String? = null, throwable: Throwable? = null)
    fun warning(tag: String?, message: String, meta: Map<String, Any?>, payload: String? = null, throwable: Throwable? = null)
    fun error(tag: String?, message: String, meta: Map<String, Any?>, payload: String? = null, throwable: Throwable? = null)
    fun verbose(tag: String?, message: String, meta: Map<String, Any?>, payload: String? = null, throwable: Throwable? = null)
    fun send(logLevel: Int, tag: String?, message: String, meta: Map<String, Any?>, payload: String? = null, throwable: Throwable? = null)

    fun logCallerFunc()

    /**
     * Enable or disable general exception logging.
     * It use [Thread.setDefaultUncaughtExceptionHandler] to catch exception to log them.
     *
     * When this function called still previous [Thread.getDefaultUncaughtExceptionHandler] should be called.
     * See [com.khosravi.devin.write.DevinExceptionLogger]
     */
    fun generalUncaughtExceptionLogging(isEnable: Boolean)

    /**
     * Log some application and device info that extracted from [context], with a reserved tag.
     */
    fun logSessionStart(context: Context, message: String? = null, payload: String? = null)
}

