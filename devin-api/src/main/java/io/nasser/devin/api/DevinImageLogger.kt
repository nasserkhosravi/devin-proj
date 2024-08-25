package io.nasser.devin.api

interface DevinImageLogger {
    fun failed(url: String, name: String? = null, payload: String? = null, throwable: Throwable? = null)
    fun downloading(url: String, name: String? = null, payload: String? = null)
    fun succeed(url: String, name: String? = null, payload: String? = null)
}