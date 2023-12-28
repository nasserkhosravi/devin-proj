package com.khosravi.devin.present.log

import com.khosravi.devin.present.date.TimePresent

class TextLogItemData(
    val text: String,
    val timePresent: TimePresent,
    val logLevel: Int,
    val meta: String?
) : LogItemData
