package com.khosravi.devin.present.present.http

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JsonSpanTextUtilTest {

    private val colors = JsonConfigColor(
        keyColor = 1,
        valueColor = 2,
        digitsAndNullValueColor = 3,
        signElementsColor = 4,
        booleanColor = 5,
    )

    /** Color of the narrowest span covering [index]; the whole text also carries a base color span. */
    private fun Spanned.colorAt(index: Int): Int =
        getSpans(index, index + 1, ForegroundColorSpan::class.java)
            .filter { getSpanStart(it) <= index && index < getSpanEnd(it) }
            .minBy { getSpanEnd(it) - getSpanStart(it) }
            .foregroundColor

    private fun span(json: String) = JsonSpanTextUtil(colors).spanJson(json)

    @Test
    fun `pretty prints before coloring`() {
        assertEquals("{\n  \"a\": 1\n}", span("""{"a":1}""").toString())
    }

    @Test
    fun `colors keys, values, signs, booleans and digits`() {
        val text = span("""{"key":"value","flag":true,"n":7}""")
        val s = text.toString()

        assertEquals(colors.signElementsColor, text.colorAt(s.indexOf('{')))
        assertEquals(colors.keyColor, text.colorAt(s.indexOf("\"key\"") + 1))
        assertEquals(colors.signElementsColor, text.colorAt(s.indexOf(':')))
        assertEquals(colors.valueColor, text.colorAt(s.indexOf("\"value\"") + 1))
        assertEquals(colors.booleanColor, text.colorAt(s.indexOf("true")))
        assertEquals(colors.digitsAndNullValueColor, text.colorAt(s.indexOf('7')))
    }

    @Test
    fun `strings inside arrays are colored as keys`() {
        val text = span("""["x"]""")
        assertEquals(colors.keyColor, text.colorAt(text.toString().indexOf('x')))
    }

    @Test
    fun `escaped quotes stay inside the string`() {
        val text = span("""{"k":"a\"b"}""")
        val s = text.toString()
        assertEquals(colors.valueColor, text.colorAt(s.indexOf('b')))
    }

    @Test
    fun `invalid json is returned with a base color only`() {
        val text = span("not json")
        assertEquals("not json", text.toString())
        assertEquals(colors.digitsAndNullValueColor, text.colorAt(0))
    }
}
