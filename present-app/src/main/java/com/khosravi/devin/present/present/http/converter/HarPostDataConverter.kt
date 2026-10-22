package com.khosravi.devin.present.present.http.converter

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.khosravi.devin.present.getString
import com.khosravi.devin.present.optString
import com.khosravi.lib.har.HarPostData
import java.lang.reflect.Type

class HarPostDataConverter : JsonDeserializer<HarPostData>, JsonSerializer<HarPostData> {

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): HarPostData {
        return json.asJsonObject.let {
            val mimeType = it.getString("mimeType")
            val textInstance: Any? = harBodyText(it.get("text"), mimeType)
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
            add("text", harBodyTextJson(src.text))
            add("params", JsonArray())
            add("comment", null)
        }
    }

}