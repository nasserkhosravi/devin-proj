package com.khosravi.devin.present.present.http

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.test.core.app.ApplicationProvider
import com.khosravi.lib.har.HarHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HttpFormatUtilsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val colors = JsonConfigColor(1, 2, 3, 4, 5)

    @Test
    fun `formatByteCount in SI and binary units`() {
        assertEquals("999 B", HttpFormatUtils.formatByteCount(999, si = true))
        assertEquals("1.0 kB", HttpFormatUtils.formatByteCount(1_000, si = true))
        assertEquals("1.5 MB", HttpFormatUtils.formatByteCount(1_500_000, si = true))
        assertEquals("1023 B", HttpFormatUtils.formatByteCount(1_023, si = false))
        assertEquals("1.0 KiB", HttpFormatUtils.formatByteCount(1_024, si = false))
        assertEquals("2.0 MiB", HttpFormatUtils.formatByteCount(2L * 1024 * 1024, si = false))
    }

    @Test
    fun `formatJson pretty prints valid json and keeps invalid input`() {
        assertEquals("{\n  \"a\": 1\n}", HttpFormatUtils.formatJson("""{"a":1}"""))
        assertEquals("{not json", HttpFormatUtils.formatJson("{not json"))
    }

    @Test
    fun `formatXml indents valid xml and keeps invalid input`() {
        val formatted = HttpFormatUtils.formatXml("<a><b>1</b></a>")
        assertTrue(formatted, formatted.contains("\n  <b>1</b>"))
        assertEquals("<a><b>", HttpFormatUtils.formatXml("<a><b>"))
    }

    @Test
    fun `formatUrlEncodedForm decodes each pair on its own line`() {
        assertEquals("q: hello world\nempty: \nflag: ", HttpFormatUtils.formatUrlEncodedForm("q=hello+world&empty=&flag"))
        assertEquals("  ", HttpFormatUtils.formatUrlEncodedForm("  "))
    }

    @Test
    fun `formatUrlEncodedForm keeps input with broken escapes`() {
        assertEquals("a=%zz", HttpFormatUtils.formatUrlEncodedForm("a=%zz"))
    }

    @Test
    fun `formatHeaders shows a message when empty`() {
        assertEquals("(header is empty)", HttpFormatUtils.formatHeaders(context, emptyList()).toString())
        assertEquals("(header is empty)", HttpFormatUtils.formatHeaders(context, null).toString())
    }

    @Test
    fun `formatHeaders lists every header`() {
        val text = HttpFormatUtils.formatHeaders(
            context,
            listOf(HarHeader("Accept", "*/*", null), HarHeader("X-Id", "7", null)),
        ).toString()
        assertTrue(text, text.contains("Accept: */*"))
        assertTrue(text, text.contains("X-Id: 7"))
    }

    @Test
    fun `spanBody leaves body untouched without a content type`() {
        assertEquals("raw", HttpFormatUtils.spanBody(colors, "raw", null, context))
    }

    @Test
    fun `spanBody colors json`() {
        val result = HttpFormatUtils.spanBody(colors, """{"a":true}""", "application/json", context)
        assertTrue(result is Spanned)
        assertTrue((result as Spanned).getSpans(0, result.length, ForegroundColorSpan::class.java).isNotEmpty())
    }

    @Test
    fun `spanBody formats json without context as text`() {
        assertEquals("{\n  \"a\": 1\n}", HttpFormatUtils.spanBody(colors, """{"a":1}""", "application/json", null))
    }

    @Test
    fun `spanBody formats xml and form bodies`() {
        assertTrue(HttpFormatUtils.spanBody(colors, "<a><b/></a>", "text/xml", context).contains("\n"))
        assertEquals("a: 1", HttpFormatUtils.spanBody(colors, "a=1", "application/x-www-form-urlencoded", context))
        assertEquals("plain", HttpFormatUtils.spanBody(colors, "plain", "text/plain", context))
    }
}
