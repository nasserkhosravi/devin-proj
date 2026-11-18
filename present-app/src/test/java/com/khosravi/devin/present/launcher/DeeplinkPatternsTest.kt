package com.khosravi.devin.present.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeeplinkPatternsTest {

    private val view = "android.intent.action.VIEW"
    private val browsable = "android.intent.category.BROWSABLE"
    private val default = "android.intent.category.DEFAULT"

    private fun data(
        scheme: String? = null, host: String? = null, port: String? = null,
        path: String? = null, pathPrefix: String? = null, pathPattern: String? = null,
    ) = ManifestData(scheme, host, port, path, pathPrefix, pathPattern)

    private fun filter(vararg data: ManifestData, exported: Boolean = true, categories: Set<String> = setOf(browsable, default)) =
        ManifestIntentFilter(exported, setOf(view), categories, data.toList())

    @Test
    fun `keeps only exported VIEW and BROWSABLE filters with a scheme`() {
        val result = deeplinkPatterns(
            listOf(
                filter(data(scheme = "a", host = "h"), categories = setOf(default)),
                filter(data(scheme = "b", host = "h"), exported = false),
                filter(data(host = "h")),
                ManifestIntentFilter(true, setOf("android.intent.action.MAIN"), setOf(browsable), listOf(data(scheme = "c"))),
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `combines schemes hosts and paths`() {
        val result = deeplinkPatterns(listOf(filter(data(scheme = "myapp"), data(scheme = "https"), data(host = "a.io"), data(pathPrefix = "/ride"))))
        assertEquals(listOf("https://a.io/ride*", "myapp://a.io/ride*"), result)
    }

    @Test
    fun `host with port and plain path`() {
        assertEquals(listOf("myapp://h:8080/p"), deeplinkPatterns(listOf(filter(data(scheme = "myapp", host = "h", port = "8080", path = "/p")))))
    }

    @Test
    fun `scheme only`() {
        assertEquals(listOf("tel:"), deeplinkPatterns(listOf(filter(data(scheme = "tel")))))
    }

    @Test
    fun `pathPattern is shown raw`() {
        assertEquals(listOf("myapp://h/.*/x"), deeplinkPatterns(listOf(filter(data(scheme = "myapp", host = "h", pathPattern = "/.*/x")))))
    }

    @Test
    fun `duplicates are dropped and the list is sorted`() {
        val result = deeplinkPatterns(
            listOf(
                filter(data(scheme = "z", host = "h")),
                filter(data(scheme = "a", host = "h")),
                filter(data(scheme = "a", host = "h")),
            )
        )
        assertEquals(listOf("a://h", "z://h"), result)
    }
}
