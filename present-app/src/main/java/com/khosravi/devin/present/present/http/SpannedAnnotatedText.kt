package com.khosravi.devin.present.present.http

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Converts the span types the HTTP formatters produce ([ForegroundColorSpan] incl.
 * [JsonSpanTextUtil.MyForegroundColorSpan], [BackgroundColorSpan], [StyleSpan] bold/italic, [UnderlineSpan])
 * into an [AnnotatedString]; other spans are ignored. Span order is kept, so a later span still wins over an
 * earlier overlapping one, as it does in a TextView.
 */
internal fun CharSequence.toAnnotatedString(): AnnotatedString {
    val spans = collectStyledSpans()
    if (spans.isEmpty()) return AnnotatedString(toString())
    return AnnotatedString.Builder(toString()).apply {
        spans.forEach { addStyle(it.style, it.start, it.end) }
    }.toAnnotatedString()
}

/** A body line: its text without the line break. */
internal class AnnotatedLine(val text: AnnotatedString)

/**
 * Splits this text into lines on '\n' (a trailing '\r' is dropped from each line) and converts each to an
 * [AnnotatedString] carrying the styles of the spans that overlap it, clipped to the line. Sweeps the spans
 * once instead of sub-sequencing the whole Spanned per line, which is quadratic for big JSON bodies.
 */
internal fun CharSequence.toAnnotatedLines(): List<AnnotatedLine> {
    val text = toString()
    val spans = collectStyledSpans().sortedBy { it.start }
    val result = mutableListOf<AnnotatedLine>()
    val active = mutableListOf<StyledSpan>()
    var nextSpan = 0
    var lineStart = 0
    while (true) {
        val newline = text.indexOf('\n', lineStart)
        val lineBreak = if (newline == -1) text.length else newline
        val lineEnd = if (lineBreak > lineStart && text[lineBreak - 1] == '\r') lineBreak - 1 else lineBreak

        while (nextSpan < spans.size && spans[nextSpan].start < lineEnd) {
            active += spans[nextSpan]
            nextSpan++
        }
        active.removeAll { it.end <= lineStart }

        val lineText = text.substring(lineStart, lineEnd)
        val annotated = if (active.isEmpty() || lineText.isEmpty()) {
            AnnotatedString(lineText)
        } else {
            AnnotatedString.Builder(lineText).apply {
                active.sortedBy { it.order }.forEach { span ->
                    val start = maxOf(span.start, lineStart) - lineStart
                    val end = minOf(span.end, lineEnd) - lineStart
                    if (start < end) addStyle(span.style, start, end)
                }
            }.toAnnotatedString()
        }
        result += AnnotatedLine(annotated)

        if (newline == -1) break
        lineStart = newline + 1
    }
    return result
}

private class StyledSpan(val style: SpanStyle, val start: Int, val end: Int, val order: Int)

private fun CharSequence.collectStyledSpans(): List<StyledSpan> {
    if (this !is Spanned) return emptyList()
    // A JSON body has a span per token but only a handful of distinct styles: share one SpanStyle per style.
    val styleCache = HashMap<Long, SpanStyle>()
    // getSpans returns spans in insertion order (priority aside), which is the order a TextView applies them.
    return getSpans(0, length, Any::class.java).mapIndexedNotNull { index, span ->
        val key = span.styleKey() ?: return@mapIndexedNotNull null
        val start = getSpanStart(span).coerceIn(0, length)
        val end = getSpanEnd(span).coerceIn(0, length)
        if (start >= end) return@mapIndexedNotNull null
        val style = styleCache.getOrPut(key) { span.toSpanStyle()!! }
        StyledSpan(style, start, end, index)
    }
}

/** Identifies the [SpanStyle] a supported span maps to (kind in the high bits, colour/style in the low 32). */
private fun Any.styleKey(): Long? {
    val (kind, value) = when (this) {
        is ForegroundColorSpan -> 1L to foregroundColor
        is BackgroundColorSpan -> 2L to backgroundColor
        is UnderlineSpan -> 3L to 0
        is StyleSpan -> if (style == Typeface.BOLD || style == Typeface.ITALIC || style == Typeface.BOLD_ITALIC) {
            4L to style
        } else {
            return null
        }

        else -> return null
    }
    return (kind shl 32) or (value.toLong() and 0xFFFFFFFFL)
}

private fun Any.toSpanStyle(): SpanStyle? = when (this) {
    is ForegroundColorSpan -> SpanStyle(color = Color(foregroundColor))
    is BackgroundColorSpan -> SpanStyle(background = Color(backgroundColor))
    is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
    is StyleSpan -> when (style) {
        Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        else -> null
    }

    else -> null
}
