package com.khosravi.devin.present.present

import com.google.gson.JsonObject
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.tool.PositiveNumber

data class ExportOptions(
    val id: String,
    val tagWhitelist: List<TagFilterItem>?,
    val withSeparationTagFiles: Boolean,
    val upToDaysNumber: PositiveNumber?,
) {

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("tagWhitelist", tagWhitelist.toString())
            addProperty("withSeparationTagFiles", withSeparationTagFiles)
            addProperty("upToDaysNumber", upToDaysNumber?.value)
        }
    }

}