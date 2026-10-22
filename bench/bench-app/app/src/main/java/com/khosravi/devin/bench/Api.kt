package com.khosravi.devin.bench

import com.khosravi.devin.write.DevinTool
import com.khosravi.devin.write.okhttp.okhttpLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor

class HttpResult(val code: Int, val body: String)

/** Talks to the shop backend. The emulator reaches the host machine at 10.0.2.2. */
class Api(private val baseUrl: String = BASE_URL) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        .apply { DevinTool.get()?.okhttpLogger?.getOrCreateInterceptor()?.let { addNetworkInterceptor(it) } }
        .build()

    fun get(path: String): HttpResult = execute(Request.Builder().url(baseUrl + path).get().build())

    fun post(path: String, json: String): HttpResult = execute(
        Request.Builder().url(baseUrl + path).post(json.toRequestBody(JSON)).build()
    )

    private fun execute(request: Request): HttpResult = client.newCall(request).execute().use {
        HttpResult(it.code, it.body?.string().orEmpty())
    }

    companion object {
        const val BASE_URL = "http://10.0.2.2:8787"
        private val JSON = "application/json".toMediaType()
    }
}
