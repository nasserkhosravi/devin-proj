package com.khosravi.devin.write


interface DevinLogger {

    fun doIfEnable(action: (logger: DevinLogger) -> Unit)

    fun debug(tag: String?, message: String, payload: String? = null)
    fun info(tag: String?, message: String, payload: String? = null)
    fun warning(tag: String?, message: String, payload: String? = null)
    fun error(tag: String?, message: String, payload: String? = null)

    fun logCallerFunc()
}

