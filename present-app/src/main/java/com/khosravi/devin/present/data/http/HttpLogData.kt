package com.khosravi.devin.present.data.http

import com.khosravi.devin.present.data.LogId

data class HttpLogData(
    val logId: LogId,
    val name: String, val url: String,
    val operationStatus: HttpLogOperationStatus, val date: Long,
    val httpMethod: String,
) {
    val urlQuery by lazy { UrlQuery.create(url) }


}