package com.khosravi.devin.write.okhttp.network.entity

import com.khosravi.devin.write.okhttp.network.support.JsonConverter

internal data class HttpRequestModel(
    val requestContentSize: Long?,
    val requestContentType: String?,
    var requestHeaders: String,
    var requestHeadersSize: Long?,
    val requestBody: String?,
    val isRequestBodyEncoded: Boolean = false,
    val method: String,
    var requestDate: Long,
    /** The stored body is only the first part of the original, see [requestBodyNote]. */
    val isRequestBodyTruncated: Boolean = false,
    /** Why the stored body differs from the original (cut, or binary), for the HAR `comment`. */
    val requestBodyNote: String? = null,
) {

    fun getHeadersAsList(): List<HttpHeaderModel> {
        return requestHeaders.let { JsonConverter.deserializeAsHttpHeaderList(it) }
    }


}

