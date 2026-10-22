package com.khosravi.devin.write

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFieldsTest {

    @Test
    fun `strings, numbers, booleans and null keep their type`() {
        val json = LogFields.toJson(mapOf("s" to "a", "i" to 3, "l" to 4L, "d" to 1.5, "b" to false, "n" to null))
        assertEquals("a", json.getString("s"))
        assertEquals(3, json.getInt("i"))
        assertEquals(4L, json.getLong("l"))
        assertEquals(1.5, json.getDouble("d"), 0.0)
        assertFalse(json.getBoolean("b"))
        assertTrue(json.isNull("n"))
    }

    @Test
    fun `enums become their name and other objects their text`() {
        val json = LogFields.toJson(mapOf("state" to Thread.State.NEW, "list" to listOf(1, 2)))
        assertEquals("NEW", json.getString("state"))
        assertEquals("[1, 2]", json.getString("list"))
    }

    @Test
    fun `NaN and infinity are stored as text, since JSON has no such numbers`() {
        val json = LogFields.toJson(mapOf("nan" to Double.NaN, "inf" to Float.POSITIVE_INFINITY))
        assertEquals("NaN", json.getString("nan"))
        assertEquals("Infinity", json.getString("inf"))
        // The object must still serialize to valid JSON.
        JSONObject(json.toString())
    }

    @Test
    fun `long strings are cut to 2000 characters with an ellipsis`() {
        val value = LogFields.toJson(mapOf("long" to "x".repeat(2100))).getString("long")
        assertEquals(2001, value.length)
        assertTrue(value.endsWith("…"))
    }

    @Test
    fun `long keys are cut to 100 characters`() {
        val json = LogFields.toJson(mapOf("k".repeat(150) to 1))
        assertEquals(setOf("k".repeat(100)), json.keys().asSequence().toSet())
    }

    @Test
    fun `at most 50 fields are kept and the rest are counted`() {
        val json = LogFields.toJson((1..60).associate { "f$it" to it })
        assertEquals(51, json.length())
        assertEquals(10, json.getInt("_dropped"))
        assertTrue(json.has("f50"))
        assertFalse(json.has("f51"))
    }
}
