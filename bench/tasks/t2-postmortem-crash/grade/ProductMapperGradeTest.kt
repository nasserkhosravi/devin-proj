package com.khosravi.devin.bench

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMapperGradeTest {

    private val body = """[{"id":1,"name":"Blue mug","price":1200},{"id":17,"name":"Gift mug","price":null},{"id":2,"name":"Notebook","price":450}]"""

    @Test
    fun `a product with a null price does not crash the list and keeps the valid products`() {
        val ids = ProductMapper.mapList(body).map { it.id }
        assertTrue("valid products missing: $ids", ids.containsAll(listOf(1, 2)))
    }

    @Test
    fun `a product with a null price is not turned into a free product`() {
        val gift = ProductMapper.mapList(body).firstOrNull { it.id == 17 }
        assertFalse("product 17 without a price became free: $gift", gift?.priceCents == 0L)
    }
}
