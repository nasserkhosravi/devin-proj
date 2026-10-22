package com.khosravi.devin.write.ext

import org.json.JSONArray
import org.json.JSONObject

inline fun JSONObject.forEach(action: (key: String, value: Any) -> Unit) {
    keys().forEach {
        action(it as String, get(it))
    }
}

inline fun JSONArray.forEach(action: (Any) -> Unit) {
    val size = length()
    for (i in 0 until size) {
        action(get(i))
    }
}


