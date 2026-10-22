package com.khosravi.devin.bench

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryGradeTest {

    @Test
    fun `highest price first sends the sort value the backend understands`() {
        val path = SearchQuery.path("mug", Sort.PRICE_DESC)
        assertTrue(path, path.contains("sort=price_desc"))
    }
}
