package com.khosravi.devin.bench

object CartCalculator {

    fun total(items: List<CartItem>): Long = items.sumOf { item ->
        val gross = item.product.priceCents * item.quantity
        val percent = item.discount?.percent ?: 0
        gross - gross * percent / 100
    }
}
