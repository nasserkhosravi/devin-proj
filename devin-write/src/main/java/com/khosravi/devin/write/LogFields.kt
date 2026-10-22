package com.khosravi.devin.write

import org.json.JSONObject

/** Turns a log's structured fields into JSON, keeping their size bounded. */
internal object LogFields {

    private const val MAX_FIELDS = 50
    private const val MAX_KEY_LENGTH = 100
    private const val MAX_STRING_LENGTH = 2000

    /** Set when fields beyond [MAX_FIELDS] were dropped: how many. */
    private const val KEY_DROPPED = "_dropped"

    fun toJson(fields: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        var dropped = 0
        for ((key, value) in fields) {
            if (json.length() >= MAX_FIELDS) {
                dropped++
                continue
            }
            json.put(key.take(MAX_KEY_LENGTH), toJsonValue(value))
        }
        if (dropped > 0) json.put(KEY_DROPPED, dropped)
        return json
    }

    private fun toJsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Boolean -> value
        // JSON has no NaN or Infinity.
        is Double -> if (value.isNaN() || value.isInfinite()) value.toString() else value
        is Float -> if (value.isNaN() || value.isInfinite()) value.toString() else value.toDouble()
        is Number -> value
        is Enum<*> -> value.name
        else -> value.toString().let { if (it.length > MAX_STRING_LENGTH) it.take(MAX_STRING_LENGTH) + "…" else it }
    }
}
