package com.khosravi.devin.present.present.http

import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchHighlightUtilTest {

    @Test
    fun `indicesOf finds every match case-insensitively`() {
        assertEquals(listOf(0, 8, 16), "token = Token = TOKEN".indicesOf("token"))
        assertEquals(emptyList<Int>(), "abc".indicesOf("x"))
    }

    @Test
    fun `highlightWithDefinedColors spans every match with three styles`() {
        val text = SpannableStringBuilder("ab ab")

        text.highlightWithDefinedColors("ab", listOf(0, 3), backgroundColor = 10, foregroundColor = 20)

        val backgrounds = text.getSpans(0, text.length, BackgroundColorSpan::class.java)
        val foregrounds = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
        val underlines = text.getSpans(0, text.length, UnderlineSpan::class.java)
        assertEquals(2, backgrounds.size)
        assertEquals(2, foregrounds.size)
        assertEquals(2, underlines.size)
        assertEquals(setOf(0 to 2, 3 to 5), backgrounds.map { text.getSpanStart(it) to text.getSpanEnd(it) }.toSet())
        assertEquals(setOf(10), backgrounds.map { it.backgroundColor }.toSet())
        assertEquals(setOf(20), foregrounds.map { it.foregroundColor }.toSet())
    }

    @Test
    fun `highlightWithDefinedColorsSubstring spans one range`() {
        val text = SpannableStringBuilder("hello world")

        text.highlightWithDefinedColorsSubstring("world", 6, backgroundColor = 1, foregroundColor = 2)

        val span = text.getSpans(0, text.length, BackgroundColorSpan::class.java).single()
        assertEquals(6, text.getSpanStart(span))
        assertEquals(11, text.getSpanEnd(span))
    }
}
