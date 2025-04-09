package com.khosravi.devin.write.okhttp.read

import com.khosravi.devin.read.DevinLogFlagsApi

object DevinHttpFlagsApi {

    const val LOG_TAG = "devin_http"
    const val KEY_URL = "url"
    const val KEY_STATUS_TYPE = "status_type"
    const val KEY_HAR = "HAR"
    const val KEY_SUMMERY_OF_ERROR = "error_summery"

    object Status {
        const val REQUEST = DevinLogFlagsApi.IN_PROGRESS
        const val NETWORK_ERROR = DevinLogFlagsApi.ERROR
        const val HTTP_CODE = DevinLogFlagsApi.FINISHED
    }

}