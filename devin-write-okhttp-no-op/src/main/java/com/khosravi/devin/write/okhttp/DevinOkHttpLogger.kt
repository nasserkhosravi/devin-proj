package com.khosravi.devin.write.okhttp

import com.khosravi.devin.write.DevinTool
import com.khosravi.devin.write.okhttp.network.support.DevinOkHttpBodyDecoder
import okhttp3.Interceptor
import okhttp3.Request

interface DevinOkHttpLogger {

    fun getOrCreateInterceptor(): Interceptor?

    fun setRequestsSkipper(action: (Request) -> Boolean)

    fun addHeadersRedactor(headers: List<String>)

    fun addBodyDecoder(decoder: DevinOkHttpBodyDecoder)
}


private var instance: DevinOkHttpLogger? = null

val DevinTool.okhttpLogger: DevinOkHttpLogger?
    get() {
        return null
    }
