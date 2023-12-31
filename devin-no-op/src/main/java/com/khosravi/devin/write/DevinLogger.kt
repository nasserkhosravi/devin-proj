package com.khosravi.devin.write

interface DevinLogger {

    fun doIfEnable(action: (logger: DevinLogger) -> Unit)
    fun log(message: String)
    fun log(tag: String, message: String = "")
    fun logCallerFunc()
    fun logCallerFunc(tag: String? = null, message: String? = null, enableParentName: Boolean = true)
}

