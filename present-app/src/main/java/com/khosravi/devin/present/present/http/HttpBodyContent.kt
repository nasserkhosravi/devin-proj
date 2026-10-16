package com.khosravi.devin.present.present.http

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.uikit.text.chunkForDisplay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Request or response side of an HTTP log, shown by one content tab. */
internal enum class HttpContentSide { Request, Response }

/** Everything a content tab renders, prepared off the main thread by [buildHttpContentModel]. */
internal class HttpContentModel(
    /** The formatted headers split by [chunkForDisplay], one list item each. */
    val headers: List<AnnotatedString>,
    /** Null when the body is missing or empty: the tab then shows `msg_body_empty` and no search. */
    val body: HttpBodyModel?,
) {
    /** List index of the first body item: the header chunks and the copy-actions row come before it. */
    val bodyItemsOffset: Int
        get() = headers.size + 1
}

internal class HttpBodyModel(
    /** The formatted body as plain text, for "Copy body". */
    val formattedText: String,
    /** Plain text of each body line (split on '\n'); searched as a whole so matches can span chunks. */
    val lines: List<String>,
    /** One list item per chunk of a line (see [chunkForDisplay]), each keeping its JSON colours. */
    val items: List<BodyDisplayItem>,
    /** Index in [items] of the first chunk of each line. */
    private val firstItemOfLine: IntArray,
) {
    /** The index in [items] of the chunk that contains the start of [match]. */
    fun itemIndexOf(match: BodyMatch): Int {
        var index = firstItemOfLine[match.lineIndex]
        while (index + 1 < items.size &&
            items[index + 1].lineIndex == match.lineIndex &&
            items[index + 1].offsetInLine <= match.start
        ) {
            index++
        }
        return index
    }
}

/** A chunk of body line [lineIndex] that starts at [offsetInLine] of that line. */
internal class BodyDisplayItem(val lineIndex: Int, val offsetInLine: Int, val text: AnnotatedString)

internal data class BodyMatch(val lineIndex: Int, val start: Int)

/** Matches of [query] in the body; each highlight is [query].length long. */
internal class BodySearchResult(val query: String, val matches: List<BodyMatch>) {
    val startsByLine: Map<Int, List<Int>> = matches.groupBy({ it.lineIndex }, { it.start })
}

/** Same rules as the old body item: headers formatted to a Spanned, body coloured by [HttpFormatUtils.spanBody]. */
internal fun buildHttpContentModel(
    context: Context,
    data: HttpLogDetailData,
    side: HttpContentSide,
    jsonConfigColor: JsonConfigColor,
): HttpContentModel {
    val isRequest = side == HttpContentSide.Request
    val headers = HttpFormatUtils.formatHeaders(context, if (isRequest) data.requestHeaders else data.responseHeaders)
    val bodyContent = if (isRequest) data.requestBody else data.responseBody
    val bodyMimeType = if (isRequest) data.requestBodyMimeType else data.responseBodyMimeType
    return HttpContentModel(
        headers = headers.toAnnotatedString().chunkedForDisplay(),
        body = buildBodyModel(context, bodyContent, bodyMimeType, jsonConfigColor),
    )
}

private fun buildBodyModel(
    context: Context,
    content: String?,
    contentType: String?,
    jsonConfigColor: JsonConfigColor,
): HttpBodyModel? {
    if (content.isNullOrEmpty()) return null
    val spanned = HttpFormatUtils.spanBody(jsonConfigColor, content, contentType, context)
    if (spanned.isEmpty()) return null

    val lines = spanned.toAnnotatedLines()
    val items = ArrayList<BodyDisplayItem>(lines.size)
    val firstItemOfLine = IntArray(lines.size)
    lines.forEachIndexed { lineIndex, line ->
        firstItemOfLine[lineIndex] = items.size
        var offset = 0
        line.text.chunkedForDisplay().forEach { chunk ->
            items += BodyDisplayItem(lineIndex, offset, chunk)
            offset += chunk.length
        }
    }
    return HttpBodyModel(
        formattedText = spanned.toString(),
        lines = lines.map { it.text.text },
        items = items,
        firstItemOfLine = firstItemOfLine,
    )
}

/** Splits this text with [chunkForDisplay], keeping each chunk's styles. */
private fun AnnotatedString.chunkedForDisplay(): List<AnnotatedString> {
    val chunks = chunkForDisplay(text)
    if (chunks.size == 1) return listOf(this)
    var offset = 0
    return chunks.map { chunk ->
        subSequence(offset, offset + chunk.length).also { offset += chunk.length }
    }
}

/**
 * Literal, case-insensitive, non-overlapping search of [query] in every line (see [literalIndicesOf]), so
 * JSON text like `{"` or `..` is matched as typed rather than as a pattern.
 */
internal suspend fun searchBody(lines: List<String>, query: String): BodySearchResult {
    val matches = mutableListOf<BodyMatch>()
    lines.forEachIndexed { lineIndex, line ->
        currentCoroutineContext().ensureActive()
        line.literalIndicesOf(query).forEach { matches += BodyMatch(lineIndex, it) }
    }
    return BodySearchResult(query, matches)
}

/** Search-highlight span styles: one for every match, one for the current match. */
internal class SearchHighlightStyles(val match: SpanStyle, val current: SpanStyle)

/** Underlined highlight styles in the theme's search colours (see `DevinColors.searchMatch*`). */
internal fun searchHighlightStyles(
    matchText: Color,
    matchBackground: Color,
    currentText: Color,
    currentBackground: Color,
) = SearchHighlightStyles(
    match = SpanStyle(color = matchText, background = matchBackground, textDecoration = TextDecoration.Underline),
    current = SpanStyle(color = currentText, background = currentBackground, textDecoration = TextDecoration.Underline),
)

/** Overlays search highlights ([styles] for all matches and for [current]) on the item's colours. */
internal fun BodyDisplayItem.highlighted(
    result: BodySearchResult?,
    current: BodyMatch?,
    styles: SearchHighlightStyles,
): AnnotatedString {
    if (result == null) return text
    val starts = result.startsByLine[lineIndex] ?: return text
    val queryLength = result.query.length
    val itemEnd = offsetInLine + text.length
    var builder: AnnotatedString.Builder? = null
    starts.forEach { start ->
        val from = maxOf(start, offsetInLine)
        val to = minOf(start + queryLength, itemEnd)
        if (from < to) {
            val isCurrent = current != null && current.lineIndex == lineIndex && current.start == start
            val b = builder ?: AnnotatedString.Builder(text).also { builder = it }
            b.addStyle(if (isCurrent) styles.current else styles.match, from - offsetInLine, to - offsetInLine)
        }
    }
    return builder?.toAnnotatedString() ?: text
}
