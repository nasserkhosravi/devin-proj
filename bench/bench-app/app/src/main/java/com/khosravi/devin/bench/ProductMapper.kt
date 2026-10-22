package com.khosravi.devin.bench

import org.json.JSONArray
import org.json.JSONObject

object ProductMapper {

    /** Returns null when the backend sends a product without a price; such products can't be sold. */
    fun map(json: JSONObject): Product? {
        if (json.isNull("price")) return null
        return Product(
            id = json.getInt("id"),
            name = json.getString("name"),
            priceCents = json.getLong("price"),
        )
    }

    fun mapList(body: String): List<Product> {
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { index ->
            val json = array.getJSONObject(index)
            map(json).also {
                if (it == null) AppLog.w(TAG, "Skipping product ${json.optInt("id")} without a price")
            }
        }
    }

    private const val TAG = "ProductMapper"
}
