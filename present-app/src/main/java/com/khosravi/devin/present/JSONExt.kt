package com.khosravi.devin.present

import org.json.JSONArray
import org.json.JSONObject
import java.util.HashMap

fun JSONArray?.isNullOrEmpty() = this == null || isEmpty()

fun JSONArray.isEmpty() = length() == 0

fun JSONObject.isEmpty() = length() == 0

fun JSONArray.isNotEmpty() = length() > 0

inline fun JSONObject.forEach(action: (key: String, value: Any) -> Unit) {
    keys().forEach {
        action(it as String, get(it))
    }
}

fun JSONObject.toMap(): Map<String, Any> {
    val length = length()
    if (length == 0) return emptyMap()

    val result = HashMap<String, Any>(length)
    keys().forEach {
        result[it] = get(it)
    }
    return result
}

inline fun JSONArray.forEach(action: (Any) -> Unit) {
    val size = length()
    for (i in 0 until size) {
        action(get(i))
    }
}

inline fun <R> JSONArray.map(transform: (Any) -> R): List<R> {
    val size = length()
    if (size == 0) {
        return emptyList()
    }
    val result = ArrayList<R>(size)
    for (index in 0 until size) {
        val data = get(index)
        result.add(transform(data))
    }
    return result
}

inline fun <R> JSONArray.mapNotNull(transform: (Any) -> R?): List<R> {
    val size = length()
    if (size == 0) {
        return emptyList()
    }
    val result = ArrayList<R>(size)
    for (index in 0 until size) {
        val data = opt(index)
        if (data != null) {
            val element = transform(data)
            if (element != null) {
                result.add(element)
            }
        }
    }
    return result
}

fun JSONObject?.isNullOrEmpty(): Boolean {
    if (this == null || length() == 0) {
        return true
    }
    return false
}

fun JSONObject?.isNotNullOrEmpty() = this?.isNullOrEmpty() == false

fun JSONArray.toList(): List<Any> {
    val length = length()
    if (length == 0) {
        return emptyList()
    }
    val result = ArrayList<Any>(length)
    forEach {
        result.add(it)
    }
    return result
}

fun JSONObject.flatPut(from: JSONObject?): JSONObject {
    from ?: return this
    from.keys().forEach {
        put(it, from.get(it))
    }
    return this
}

fun JSONObject.optIntOrNull(key: String): Int? {
    if (has(key)) {
        return optInt(key)
    }
    return null
}

fun JSONObject.optStringOrNull(key: String): String? {
    if (has(key)) {
        return getString(key)
    }
    return null
}