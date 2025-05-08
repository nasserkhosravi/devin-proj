package com.khosravi.devin.present.present.http

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.khosravi.devin.present.present.http.converter.HarContentConverter
import com.khosravi.devin.present.present.http.converter.HarPostDataConverter
import com.khosravi.lib.har.HarContent
import com.khosravi.lib.har.HarPostData

internal object GsonConverter {
    private val nonNullSerializerInstance: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create()
    }

    val instance: Gson by lazy {
        nonNullSerializerInstance.newBuilder()
            .serializeNulls()
            .registerTypeAdapter(HarPostData::class.java, HarPostDataConverter())
            .registerTypeAdapter(HarContent::class.java, HarContentConverter())
            .create()
    }

    fun parseString(json: String): JsonElement? {
        return JsonParser.parseString(json)
    }

    fun parseStringSafe(json: String): JsonElement? {
        return try {
            parseString(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}