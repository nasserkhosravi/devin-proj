package com.khosravi.devin.present

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class JSONExtTest {

    @Test
    fun `array emptiness helpers`() {
        val nullArray: JSONArray? = null
        assertTrue(nullArray.isNullOrEmpty())
        assertTrue(JSONArray().isNullOrEmpty())
        assertTrue(JSONArray().isEmpty())
        assertFalse(JSONArray().put(1).isEmpty())
        assertTrue(JSONArray().put(1).isNotEmpty())
    }

    @Test
    fun `object emptiness helpers`() {
        val nullObject: JSONObject? = null
        assertTrue(nullObject.isNullOrEmpty())
        assertTrue(JSONObject().isNullOrEmpty())
        assertFalse(JSONObject().isNotNullOrEmpty())
        assertTrue(JSONObject().put("a", 1).isNotNullOrEmpty())
    }

    @Test
    fun `array map transforms in order`() {
        assertEquals(listOf(2, 4, 6), JSONArray(listOf(1, 2, 3)).map { (it as Int) * 2 })
        assertEquals(emptyList<Int>(), JSONArray().map { it })
    }

    @Test
    fun `array mapNotNull drops null results and json nulls`() {
        val array = JSONArray().put(1).put(JSONObject.NULL).put(2).put(3)
        val result = array.mapNotNull { if (it == 2) null else it }
        assertEquals(listOf(1, JSONObject.NULL, 3), result)
    }

    @Test
    fun `array forEach and toList visit every element`() {
        val array = JSONArray(listOf("a", "b"))
        val visited = mutableListOf<Any>()
        array.forEach { visited.add(it) }
        assertEquals(listOf("a", "b"), visited)
        assertEquals(listOf("a", "b"), array.toList())
        assertEquals(emptyList<Any>(), JSONArray().toList())
    }

    @Test
    fun `object forEach visits every key`() {
        val visited = mutableMapOf<String, Any>()
        JSONObject().put("a", 1).put("b", "x").forEach { key, value -> visited[key] = value }
        assertEquals(mapOf("a" to 1, "b" to "x"), visited)
    }

    @Test
    fun `flatPut copies keys, overwriting existing ones`() {
        val target = JSONObject().put("a", 1).put("b", 2)
        val result = target.flatPut(JSONObject().put("b", 20).put("c", 30))

        assertSame(target, result)
        assertEquals(1, result.getInt("a"))
        assertEquals(20, result.getInt("b"))
        assertEquals(30, result.getInt("c"))
    }

    @Test
    fun `flatPut with null source is a no-op`() {
        val target = JSONObject().put("a", 1)
        assertSame(target, target.flatPut(null))
        assertEquals(1, target.length())
    }
}
