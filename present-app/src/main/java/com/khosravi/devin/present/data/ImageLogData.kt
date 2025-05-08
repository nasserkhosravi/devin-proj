package com.khosravi.devin.present.data

import com.khosravi.devin.read.DevinImageFlagsApi

data class ImageLogData(val name: String, val url: String, val status: Int, val date: Long) {
    fun isFailed() = status == DevinImageFlagsApi.Status.FAILED
}