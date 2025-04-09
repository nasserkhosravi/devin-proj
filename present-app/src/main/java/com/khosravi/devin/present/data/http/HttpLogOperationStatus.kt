package com.khosravi.devin.present.data.http

import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import com.khosravi.lib.har.HarResponse

sealed interface HttpLogOperationStatus {
    data object Requested : HttpLogOperationStatus
    data class Respond(val status: Int) : HttpLogOperationStatus
    data object NetworkFailed : HttpLogOperationStatus
    data object Unsupported : HttpLogOperationStatus

    companion object {

        fun fromCode(code: Int, response: HarResponse?): HttpLogOperationStatus {
            return when (code) {
                DevinHttpFlagsApi.Status.REQUEST -> Requested
                DevinHttpFlagsApi.Status.HTTP_CODE -> Respond(response!!.status)
                DevinHttpFlagsApi.Status.NETWORK_ERROR -> NetworkFailed
                else -> Unsupported
            }
        }
    }
}