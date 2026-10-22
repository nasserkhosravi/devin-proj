package com.khosravi.devin.bench

import org.junit.Assert.assertEquals
import org.junit.Test

class CartCalculatorGradeTest {

    private val mug = Product(1, "Blue mug", 1000)

    @Test
    fun `an item without a discount counts at full price`() {
        assertEquals(2000L, CartCalculator.total(listOf(CartItem(mug, quantity = 2))))
    }

    @Test
    fun `a discount is still applied`() {
        assertEquals(1800L, CartCalculator.total(listOf(CartItem(mug, quantity = 2, discount = Discount(10)))))
    }
}
