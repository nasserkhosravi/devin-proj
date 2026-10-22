package com.khosravi.devin.bench

data class Product(val id: Int, val name: String, val priceCents: Long)

data class Discount(val percent: Int)

data class CartItem(val product: Product, val quantity: Int, val discount: Discount? = null)

enum class Sort(val apiValue: String) {
    RELEVANCE("relevance"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc"),
}

sealed class CheckoutResult {
    data class Success(val orderId: String) : CheckoutResult()
    data class Failure(val code: String, val message: String) : CheckoutResult()
}
