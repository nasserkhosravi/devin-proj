package com.khosravi.devin.write.okhttp.network.entity

import com.khosravi.devin.write.okhttp.network.support.JsonParser

internal data class HttpRequestModel(
    val requestContentSize: Long?,
    val requestContentType: String?,
    var requestHeaders: String,
    var requestHeadersSize: Long?,
    val requestBody: String?,
    val isRequestBodyEncoded: Boolean = false,
    val method: String,
    var requestDate: Long
) {

    fun getHeadersAsList(): List<HttpHeaderModel> {
        return requestHeaders.let { JsonParser.deserializeAsHttpHeaderList(it) }
    }


}

