package com.khosravi.devin.write.okhttp.network.support

import com.khosravi.devin.write.ext.forEach
import com.khosravi.devin.write.okhttp.network.entity.HttpHeaderModel
import org.json.JSONArray
import org.json.JSONObject

internal object JsonParser {

    private const val KEY_HEADER_NAME = "name"
    private const val KEY_HEADER_VALUE = "value"

    fun serialize(model: List<HttpHeaderModel>): String {
        val jsonArray = JSONArray()
        model.forEach {
            val obj = JSONObject()
                .put(KEY_HEADER_NAME, it.name)
                .put(KEY_HEADER_VALUE, it.value).toString()
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    fun deserializeAsHttpHeaderList(json: String): List<HttpHeaderModel> {
        val result = ArrayList<HttpHeaderModel>()
        return try {
            val jsonArray = JSONArray(json)
            jsonArray.forEach {
                val obj: JSONObject? = when (it) {
                    is String -> JSONObject(it)
                    is JSONObject -> it
                    else -> null
                }

                if (obj != null) {
                    val name = obj.getString(KEY_HEADER_NAME)
                    val value = obj.getString(KEY_HEADER_VALUE)
                    result.add(HttpHeaderModel(name, value))
                }

            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            result
        }
    }

}