package com.khosravi.devin.bench

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Runs one user flow, chosen by the "scenario" intent extra:
 * browse, search, cart, checkout.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private val repository = ShopRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 18f; setPadding(48, 48, 48, 48) }
        setContentView(status)

        val scenario = intent.getStringExtra("scenario") ?: "browse"
        AppLog.i(TAG, "Scenario: $scenario")
        executor.execute { run(scenario) }
    }

    private fun run(scenario: String) {
        val message = when (scenario) {
            "browse" -> "Products: " + repository.products().joinToString { it.name }
            "search" -> "Highest price first: " + repository.search("mug", Sort.PRICE_DESC).joinToString { it.name }
            "cart" -> {
                val total = CartCalculator.total(sampleCart(repository.products()))
                AppLog.i(TAG, "Cart total: $total cents")
                "Cart total: ${total / 100.0}"
            }
            "checkout" -> {
                val cart = sampleCart(repository.products())
                when (val result = repository.checkout(cart, coupon = "SUMMER")) {
                    is CheckoutResult.Success -> "Order placed: ${result.orderId}".also { AppLog.i(TAG, it) }
                    is CheckoutResult.Failure -> "Checkout failed: ${result.message}".also { AppLog.w(TAG, "${result.code}: ${result.message}") }
                }
            }
            else -> "Unknown scenario $scenario"
        }
        runOnUiThread { status.text = message }
    }

    private fun sampleCart(products: List<Product>): List<CartItem> = listOf(
        CartItem(products[0], quantity = 2, discount = Discount(10)),
        CartItem(products[1], quantity = 1),
    )

    private companion object {
        const val TAG = "MainActivity"
        val executor = Executors.newSingleThreadExecutor()
    }
}
