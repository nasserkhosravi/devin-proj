package com.khosravi.devin.present.log

import com.google.gson.JsonObject
import com.khosravi.devin.present.date.TimePresent
import java.io.Serializable

class TextLogItemData(
    val tag: String,
    val text: String,
    val timePresent: TimePresent,
    val logLevel: Int,
    val meta: JsonObject?
) : LogItemData, Serializable

//TODO, fix RuntimeException: Parcelable encountered IOException writing serializable object (name = com.khosravi.devin.present.log.TextLogItemData)