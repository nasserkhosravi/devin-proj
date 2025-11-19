package com.khosravi.devin.present.present

import com.google.gson.JsonObject
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.tool.PositiveNumber

data class ExportOptions(
    val id: String,
    val tagWhitelist: List<TagFilterItem>?,
    val withSeparationTagFiles: Boolean,
    val upToDaysNumber: PositiveNumber?,
) {

    companion object {
        const val KEY_WHITELIST = "tagWhitelist"
        const val KEY_WITH_SEPARATION_FILES = "withSeparationTagFiles"
        const val KEY_UP_TO_DAYS_NUMBER = "upToDaysNumber"
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("id", id)
            addProperty(KEY_WHITELIST, InterAppJsonConverter.codeTagsToWhitelist(tagWhitelist))
            addProperty(KEY_WITH_SEPARATION_FILES, withSeparationTagFiles)
            addProperty(KEY_UP_TO_DAYS_NUMBER, upToDaysNumber?.value)
        }
    }

    override fun toString(): String {
        return "ExportOptions(id='$id', tagWhitelist=${InterAppJsonConverter.codeTagsToWhitelist(tagWhitelist)}, withSeparationTagFiles=$withSeparationTagFiles, upToDaysNumber=$upToDaysNumber)"
    }


}