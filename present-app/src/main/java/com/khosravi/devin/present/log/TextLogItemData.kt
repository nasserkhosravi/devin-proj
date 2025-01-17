package com.khosravi.devin.present.log

import com.khosravi.devin.present.date.TimePresent
import java.io.Serializable

class TextLogItemData(
    val tag: String,
    val text: String,
    val timePresent: TimePresent,
    val logLevel: Int,
    val meta: String?
) : LogItemData, Serializable

//TODO, fix RuntimeException: Parcelable encountered IOException writing serializable object (name = com.khosravi.devin.present.log.TextLogItemData)