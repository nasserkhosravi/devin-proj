package com.khosravi.devin.present.present

import com.khosravi.devin.present.tool.PositiveNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportOptionsTest {

    @Test
    fun `toJson writes every option`() {
        val json = ExportOptions(
            id = "export-1",
            tagWhitelist = null,
            withSeparationTagFiles = true,
            upToDaysNumber = PositiveNumber(7),
        ).toJson()

        assertEquals("export-1", json.get("id").asString)
        assertEquals("null", json.get("tagWhitelist").asString)
        assertEquals(true, json.get("withSeparationTagFiles").asBoolean)
        assertEquals(7, json.get("upToDaysNumber").asInt)
    }

    @Test
    fun `toJson writes json null when no day limit`() {
        val json = ExportOptions("e", emptyList(), false, null).toJson()
        assertTrue(json.get("upToDaysNumber").isJsonNull)
        assertEquals("[]", json.get("tagWhitelist").asString)
    }
}
