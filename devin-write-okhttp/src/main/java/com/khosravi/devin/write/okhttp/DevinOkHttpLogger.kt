package com.khosravi.devin.write.okhttp

import com.khosravi.devin.write.DevinTool
import com.khosravi.devin.write.okhttp.network.support.DevinOkHttpBodyDecoder
import okhttp3.Interceptor
import okhttp3.Request

interface DevinOkHttpLogger {

    fun getOrCreateInterceptor(): Interceptor?

    fun setRequestsSkipper(action: (Request) -> Boolean)

    /**
     * Redaction masks sensitive values before a call is stored, as `[REDACTED#a3f9]` (a short fingerprint, so
     * calls that sent the same value can be matched). It is on by default for credential headers
     * (`Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key`, ...), fields whose name contains a word like token,
     * password, secret, phone, mobile, email or otp (in JSON bodies, form bodies and query parameters), and
     * email addresses, phone numbers and bearer tokens in any text. Use the methods below to add rules or turn
     * the defaults off.
     */
    fun addHeadersRedactor(headers: List<String>)

    /** Also masks JSON fields, form fields and query parameters with these names (case and `_`/`-` ignored). */
    fun addFieldsRedactor(fields: List<String>)

    /** Also masks text matching these patterns, in bodies and error messages. */
    fun addPatternsRedactor(patterns: List<Regex>)

    /** Turns the default rules on or off. Rules added with the methods above always apply. */
    fun setDefaultRedactionEnabled(enabled: Boolean)

    /**
     * Request and response bodies larger than [bytes] are stored cut to their first [bytes] (default 32 KB), and
     * the HAR entry's `comment` says so. Binary bodies (images, protobuf, ...) are never stored; a short note like
     * `[binary body, 1.2 MB, image/png]` takes their place. Pass 0 or less to store bodies whole.
     */
    fun setMaxBodySize(bytes: Long)

    fun addBodyDecoder(decoder: DevinOkHttpBodyDecoder)
}


private var instance: DevinOkHttpLogger? = null

val DevinTool.okhttpLogger: DevinOkHttpLogger?
    get() {
        if (instance == null) {
            connectPlugin {
                instance = OkHttpLoggerImpl(it)
            }
        }
        return instance
    }
