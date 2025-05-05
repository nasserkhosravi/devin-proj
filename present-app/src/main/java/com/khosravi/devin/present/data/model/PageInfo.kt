package com.khosravi.devin.present.data.model

data class PageInfo(
    val index: Int = 0,
    val count: Int = 40,
    var isFinished: Boolean = false,
)