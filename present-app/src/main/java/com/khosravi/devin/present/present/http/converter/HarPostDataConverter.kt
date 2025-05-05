package com.khosravi.devin.present.present.http.converter

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.getString
import com.khosravi.devin.present.optString
import com.khosravi.lib.har.HarPostData
import java.lang.reflect.Type

class HarPostDataConverter : JsonDeserializer<HarPostData>, JsonSerializer<HarPostData> {

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): HarPostData {
        return json.asJsonObject.let {
            val mimeType = it.getString("mimeType")
            val jsonText = it.get("text")
            val textInstance: Any? = if (mimeType.contains(MIME_APP_JSON)) {
                jsonText
            } else {
                jsonText.toString()
            }
            HarPostData(
                mimeType = mimeType,
                emptyList(),
                textInstance,
                it.optString("comment"),
            )
        }
    }

    override fun serialize(src: HarPostData, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonObject().apply {
            addProperty("mimeType", src.mimeType)
            val textInstance = src.text
            val text = if (textInstance is JsonObject) textInstance
            else JsonPrimitive(textInstance.toString())

            add("text", text)
            add("params", JsonArray())
            add("comment", null)
        }
    }

}