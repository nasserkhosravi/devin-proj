package com.khosravi.devin.read


object DevinImageFlagsApi {

    const val LOG_TAG = "devin_image"
    const val VALUE_IMAGE_META_TYPE = "image"
    const val KEY_IMAGE_URL = "url"
    const val KEY_IMAGE_STATUS = "status"

    object Status {
        const val DOWNLOADING = DevinLogFlagsApi.IN_PROGRESS
        const val FAILED = DevinLogFlagsApi.ERROR
        const val SUCCEED = DevinLogFlagsApi.FINISHED
    }
}

