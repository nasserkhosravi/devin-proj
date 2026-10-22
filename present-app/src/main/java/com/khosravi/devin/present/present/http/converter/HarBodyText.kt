package com.khosravi.devin.present.present.http.converter

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.khosravi.devin.present.MIME_APP_JSON

/**
 * A HAR body's stored `text` as the app uses it: a JSON object or array for JSON bodies, and plain text otherwise.
 *
 * A body stored as a JSON string (plain text, a JSON body the client cut to its size limit, or a note standing in for
 * a binary body) is unwrapped to its value. `toString()` on it would add quotes and escapes, which the body view,
 * "copy body" and "copy as cURL" then showed.
 */
internal fun harBodyText(text: JsonElement?, mimeType: String): Any? = when {
    text == null || text.isJsonNull -> null
    text.isJsonPrimitive && text.asJsonPrimitive.isString -> text.asString
    mimeType.contains(MIME_APP_JSON) -> text
    else -> text.toString()
}

/** The reverse of [harBodyText]: JSON stays JSON, anything else is stored as a JSON string. */
internal fun harBodyTextJson(text: Any?): JsonElement = when (text) {
    null -> JsonNull.INSTANCE
    is JsonElement -> text
    else -> JsonPrimitive(text.toString())
}
