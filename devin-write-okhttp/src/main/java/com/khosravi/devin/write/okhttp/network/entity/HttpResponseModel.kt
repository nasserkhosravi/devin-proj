package com.khosravi.devin.write.okhttp.network.entity

import com.khosravi.devin.write.okhttp.network.support.JsonConverter
import java.net.HttpURLConnection

internal data class HttpResponseModel(
    val responseHeadersSize: Long,
    val responseHeaders: String,
    val responseDate: Long,
    val protocol: String,
    val responseCode: Int,
    val responseMessage: String,
    val responseTlsVersion: String?,
    val responseCipherSuite: String?,
    val responseContentType: String?,
    val tookMs: Long,
    val decodedBody: String?,
    val responseBodySize: Long,

    val connection: String?,
    val serverIpAddress: String?,
) {
    fun getHeadersAsList(): List<HttpHeaderModel> {
        return responseHeaders.let { JsonConverter.deserializeAsHttpHeaderList(it) } ?: emptyList()
    }

    fun getPreferredResponseBodySize(): Long {
        return if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            0
        } else {
            responseBodySize
        }
    }

}