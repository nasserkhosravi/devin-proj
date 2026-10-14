package com.khosravi.devin.present

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile

/** Pure-JVM helpers in Ext.kt and AppExt.kt. */
class ExtTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `withPadding pads single digits`() {
        assertEquals("00", 0.withPadding())
        assertEquals("09", 9.withPadding())
        assertEquals("10", 10.withPadding())
        assertEquals("123", 123.withPadding())
    }

    @Test
    fun `itsNotEmpty wraps non-empty strings`() {
        assertEquals("a", "a".itsNotEmpty().value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `itsNotEmpty rejects empty strings`() {
        "".itsNotEmpty()
    }

    @Test
    fun `toSafeJSONObject parses valid json`() {
        assertEquals(1, """{"a":1}""".toSafeJSONObject()?.getInt("a"))
    }

    @Test
    fun `toSafeJSONObject returns null and reports invalid json`() {
        var reported = false
        assertNull("{oops".toSafeJSONObject { reported = true })
        assertEquals(true, reported)
    }

    @Test
    fun `toSafeJSONObject returns null for empty string without reporting`() {
        var reported = false
        assertNull("".toSafeJSONObject { reported = true })
        assertEquals(false, reported)
    }

    @Test
    fun `gson getters read present values`() {
        val json = JsonObject().apply {
            addProperty("i", 7)
            addProperty("s", "x")
        }
        assertEquals(7, json.getInt("i"))
        assertEquals(7, json.optInt("i"))
        assertEquals("x", json.getString("s"))
        assertEquals("x", json.optString("s"))
        assertNotNull(json.opt("i"))
    }

    @Test
    fun `gson optional getters return null for missing or json-null values`() {
        val json = JsonObject().apply { add("n", JsonNull.INSTANCE) }
        assertNull(json.optInt("missing"))
        assertNull(json.optString("missing"))
        assertNull(json.optInt("n"))
        assertNull(json.optString("n"))
        assertNull(json.opt("missing"))
        assertEquals(JsonNull.INSTANCE, json.opt("n"))
    }

    @Test
    fun `humanReadableByteCountSI formats with SI units`() {
        assertEquals("0 B", humanReadableByteCountSI(0))
        assertEquals("999 B", humanReadableByteCountSI(999))
        assertEquals(String.format("%.1f %cB", 1.0, 'k'), humanReadableByteCountSI(1000))
        assertEquals(String.format("%.1f %cB", 1.5, 'M'), humanReadableByteCountSI(1_500_000))
        assertEquals("-999 B", humanReadableByteCountSI(-999))
    }

    @Test
    fun `export file names`() {
        assertEquals("Devin_2026-10-14.zip", createZipFileNameForExport("2026-10-14"))
        assertEquals("Devin_2026-10-14.json", createJsonFileNameForExport("2026-10-14"))
    }

    @Test
    fun `formatFileSize uses decimal units`() {
        val empty = tempFolder.newFile("empty")
        assertEquals("0 Bytes", empty.formatFileSize())

        val small = tempFolder.newFile("small").apply { writeBytes(ByteArray(500)) }
        assertEquals("500 Bytes", small.formatFileSize())

        val kb = tempFolder.newFile("kb").apply { writeBytes(ByteArray(1500)) }
        assertEquals(java.text.DecimalFormat("#,##0.#").format(1.5) + " KB", kb.formatFileSize())
    }

    @Test
    fun `zipFiles stores every file by name with its content`() {
        val a = tempFolder.newFile("a.txt").apply { writeText("alpha") }
        val b = tempFolder.newFile("b.json").apply { writeText("{}") }
        val zip = tempFolder.root.resolve("out.zip")

        zipFiles(listOf(a, b), zip)

        ZipFile(zip).use { zipFile ->
            val entries = zipFile.entries().toList().associate { entry ->
                entry.name to zipFile.getInputStream(entry).bufferedReader().readText()
            }
            assertEquals(mapOf("a.txt" to "alpha", "b.json" to "{}"), entries)
        }
    }

    @Test
    fun `copyFileToOutputStream copies all bytes`() {
        val bytes = ByteArray(5000) { it.toByte() }
        val file = tempFolder.newFile("data.bin").apply { writeBytes(bytes) }
        val out = ByteArrayOutputStream()

        copyFileToOutputStream(file, out)

        assertEquals(bytes.toList(), out.toByteArray().toList())
    }

    @Test
    fun `stream text helpers round-trip utf8`() {
        val out = ByteArrayOutputStream()
        out.writeTextAndClose("سلام devin")
        assertEquals("سلام devin", ByteArrayInputStream(out.toByteArray()).readTextAndClose())
    }
}
