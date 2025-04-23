package com.khosravi.devin.present.present.http.converter

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.getInt
import com.khosravi.devin.present.getString
import com.khosravi.devin.present.opt
import com.khosravi.devin.present.optInt
import com.khosravi.devin.present.optString
import com.khosravi.lib.har.HarContent
import java.lang.reflect.Type

class HarContentConverter : JsonDeserializer<HarContent>, JsonSerializer<HarContent> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): HarContent {
        val jsonObject = json.asJsonObject
        val mimeType = jsonObject.getString("mimeType")
        val textInstance: Any? = jsonObject.opt("text")?.let { jsonText->
            if (mimeType.contains(MIME_APP_JSON)) {
                jsonText
            } else {
                jsonText.asString
            }
        }
        return HarContent(
            size = jsonObject.getInt("size"),
            compression = jsonObject.optInt("compression"),
            mimeType = mimeType,
            text = textInstance,
            encoding = jsonObject.optString("encoding"),
            comment = jsonObject.optString("comment")
        )
    }

    override fun serialize(src: HarContent, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonObject().apply {
            addProperty("size", src.size)
            addProperty("compression", src.compression)
            addProperty("mimeType", src.mimeType)

            val textInstance = src.text
            val text = if (textInstance is JsonObject) textInstance
            else JsonPrimitive(textInstance.toString())
            add("text", text)

            addProperty("encoding", src.encoding)
            add("comment", null)
        }
    }

}