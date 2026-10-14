package com.khosravi.devin.present.formatter

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterAppJsonConverterTest {

    @Test
    fun `import reads every log object`() {
        val json = """
            {"logs":[
              {"_tag":"net","_value":"GET /","_date":1000,"_meta":{"status":200},"_clientId":"app.a","_typeId":"http"},
              {"_tag":"ui","_value":"click","_date":2000}
            ]}
        """.trimIndent()

        val logs = InterAppJsonConverter.import(json)

        assertEquals(2, logs.size)
        with(logs[0]) {
            assertEquals(0L, id)
            assertEquals("net", tag)
            assertEquals("GET /", value)
            assertEquals(1000L, date)
            assertEquals(200, meta?.get("status")?.asInt)
            assertEquals("app.a", packageId)
            assertEquals("http", typeId)
        }
        with(logs[1]) {
            assertNull(meta)
            assertEquals("No client id", packageId)
            assertNull(typeId)
        }
    }

    @Test
    fun `import skips non-object entries`() {
        val logs = InterAppJsonConverter.import("""{"logs":[1,"x",{"_tag":"t","_value":"v","_date":5}]}""")
        assertEquals(1, logs.size)
        assertEquals("t", logs[0].tag)
    }

    @Test
    fun `import returns empty list for invalid input`() {
        assertTrue(InterAppJsonConverter.import("not json").isEmpty())
        assertTrue(InterAppJsonConverter.import("""{"no_logs":[]}""").isEmpty())
        assertTrue(InterAppJsonConverter.import("""{"logs":[{"_value":"missing tag"}]}""").isEmpty())
    }

    @Test
    fun `exportHARContent wraps the entry with agent and client info`() {
        val entry = JsonObject().apply { addProperty("startedDateTime", "2026-10-14T00:00:00Z") }

        val exported = JsonParser.parseString(
            InterAppJsonConverter.exportHARContent(entry, clientId = "app.a", exporterId = "presenter.id", versionName = "4.6.0")
        ).asJsonObject

        val agent = exported.getAsJsonObject("agent")
        assertEquals("presenter.id", agent.get("name").asString)
        assertEquals("4.6.0", agent.get("version_name").asString)
        assertEquals("default HAR", agent.getAsJsonObject("export_config").get("id").asString)
        assertEquals("app.a", exported.get("_clientId").asString)
        assertEquals(entry, exported.get("HAR"))
    }
}
