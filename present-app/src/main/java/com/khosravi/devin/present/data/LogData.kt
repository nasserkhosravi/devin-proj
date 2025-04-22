package com.khosravi.devin.present.data

import com.google.gson.JsonObject

class LogData(
    val id: Long,
    val tag: String,
    val value: String,
    val date: Long,
    val meta: JsonObject?,
    val packageId: String,
)