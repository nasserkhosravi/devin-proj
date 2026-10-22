package com.khosravi.devin.bench

import org.json.JSONArray
import org.json.JSONObject

class ShopRepository(private val api: Api = Api()) {

    fun products(): List<Product> {
        val result = api.get("/products")
        return ProductMapper.mapList(result.body).also {
            AppLog.i(TAG, "Loaded ${it.size} products")
        }
    }

    fun search(text: String, sort: Sort): List<Product> {
        val result = api.get(SearchQuery.path(text, sort))
        return ProductMapper.mapList(result.body).also {
            AppLog.i(TAG, "Search '$text' sorted by ${sort.name}: ${it.map { p -> p.name }}")
        }
    }

    fun checkout(items: List<CartItem>, coupon: String?): CheckoutResult {
        val body = JSONObject()
            .put("items", JSONArray(items.map { JSONObject().put("productId", it.product.id).put("quantity", it.quantity) }))
            .put("coupon", coupon ?: JSONObject.NULL)
            .put("totalCents", CartCalculator.total(items))
        val result = api.post("/checkout", body.toString())
        return CheckoutParser.parse(result.code, result.body)
    }

    private companion object {
        const val TAG = "ShopRepository"
    }
}
