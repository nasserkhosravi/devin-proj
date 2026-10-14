package com.khosravi.devin.present.present.http

import com.google.gson.JsonObject
import com.khosravi.lib.har.HarContent
import com.khosravi.lib.har.HarPostData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers HarContentConverter and HarPostDataConverter through the configured [GsonConverter.instance]. */
class HarConvertersTest {

    private val gson = GsonConverter.instance

    @Test
    fun `content with json mime keeps text as json element`() {
        val content = gson.fromJson(
            """{"size":12,"compression":3,"mimeType":"application/json; charset=utf-8","text":{"a":1},"encoding":"base64","comment":"c"}""",
            HarContent::class.java,
        )
        assertEquals(12, content.size)
        assertEquals(3, content.compression)
        assertEquals("application/json; charset=utf-8", content.mimeType)
        assertTrue(content.text is JsonObject)
        assertEquals(1, (content.text as JsonObject).get("a").asInt)
        assertEquals("base64", content.encoding)
        assertEquals("c", content.comment)
    }

    @Test
    fun `content with non-json mime keeps text as its json string form`() {
        val content = gson.fromJson("""{"size":2,"mimeType":"text/plain","text":"hi"}""", HarContent::class.java)
        assertEquals("\"hi\"", content.text)
    }

    @Test
    fun `content optional fields default to null`() {
        val content = gson.fromJson("""{"size":0,"mimeType":"text/plain"}""", HarContent::class.java)
        assertNull(content.compression)
        assertNull(content.text)
        assertNull(content.encoding)
        assertNull(content.comment)
    }

    @Test
    fun `content serializes json text as object and other text as string`() {
        val jsonText = JsonObject().apply { addProperty("k", "v") }
        val asJson = gson.toJsonTree(HarContent(5, null, "application/json", jsonText, null, "ignored")).asJsonObject
        assertEquals(jsonText, asJson.get("text"))
        assertEquals(5, asJson.get("size").asInt)
        assertTrue(asJson.get("comment").isJsonNull)

        val asString = gson.toJsonTree(HarContent(5, null, "text/plain", "raw", null, null)).asJsonObject
        assertEquals("raw", asString.get("text").asString)
    }

    @Test
    fun `post data with json mime keeps text as json element`() {
        val postData = gson.fromJson(
            """{"mimeType":"application/json","text":{"id":7},"params":[],"comment":"c"}""",
            HarPostData::class.java,
        )
        assertTrue(postData.text is JsonObject)
        assertEquals("c", postData.comment)
        assertTrue(postData.params.isEmpty())
    }

    @Test
    fun `post data with form mime keeps text as its json string form`() {
        val postData = gson.fromJson(
            """{"mimeType":"application/x-www-form-urlencoded","text":"a=1"}""",
            HarPostData::class.java,
        )
        assertEquals("\"a=1\"", postData.text)
    }

    @Test
    fun `post data serializes with empty params and null comment`() {
        val json = gson.toJsonTree(HarPostData("text/plain", emptyList(), "body", "c")).asJsonObject
        assertEquals("text/plain", json.get("mimeType").asString)
        assertEquals("body", json.get("text").asString)
        assertEquals(0, json.getAsJsonArray("params").size())
        assertTrue(json.get("comment").isJsonNull)
    }

    @Test
    fun `parseStringSafe returns null for invalid json`() {
        assertNull(GsonConverter.parseStringSafe("{"))
        assertEquals(1, GsonConverter.parseStringSafe("""{"a":1}""")?.asJsonObject?.get("a")?.asInt)
    }
}
