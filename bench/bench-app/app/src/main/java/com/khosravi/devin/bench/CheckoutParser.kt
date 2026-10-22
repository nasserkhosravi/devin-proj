package com.khosravi.devin.bench

import org.json.JSONObject

object CheckoutParser {

    fun parse(httpCode: Int, body: String): CheckoutResult {
        if (httpCode !in 200..299) return CheckoutResult.Failure("HTTP_$httpCode", "Server error")
        val json = JSONObject(body)
        // The backend reports business errors with HTTP 200 and status "error".
        if (json.optString("status") == "error") {
            return CheckoutResult.Failure(json.optString("code"), json.optString("message"))
        }
        return CheckoutResult.Success(json.getString("orderId"))
    }
}
