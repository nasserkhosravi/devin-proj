package com.khosravi.devin.present.uikit.text

/**
 * Splits [text] into consecutive pieces of at most [maxChars] so no single Text gets too tall to measure
 * (Foundation 1.5.2 throws once a Text's height exceeds what Constraints can represent). Prefers cutting
 * right after the last '\n' in the window and never splits a surrogate pair. Concatenating the result
 * gives back [text] exactly.
 */
internal fun chunkForDisplay(text: String, maxChars: Int = MAX_CHARS_PER_TEXT): List<String> {
    require(maxChars >= 2) { "maxChars must be at least 2 to make progress around surrogate pairs" }
    if (text.length <= maxChars) return listOf(text)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        if (text.length - start <= maxChars) {
            chunks += text.substring(start)
            break
        }
        val windowEnd = start + maxChars
        val lastNewline = (windowEnd - 1 downTo start).firstOrNull { text[it] == '\n' } ?: -1
        var cut = if (lastNewline >= start) {
            lastNewline + 1
        } else {
            windowEnd
        }
        if (cut == windowEnd && text[cut - 1].isHighSurrogate()) cut -= 1
        chunks += text.substring(start, cut)
        start = cut
    }
    return chunks
}

/**
 * Worst case is one char per line: 1000 lines x ~52px (20sp line height at 1x font scale) is about
 * 52k px; even ~140px lines (2x font scale on a high-density screen) stay near 140k px, well under the
 * ~262k px Constraints limit.
 */
internal const val MAX_CHARS_PER_TEXT = 1000
