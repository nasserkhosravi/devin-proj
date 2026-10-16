package com.khosravi.devin.present.present.logitem

// List rows only show a preview; the full text lives in the detail pages. A single Text taller than
// Constraints can represent (~262k px) crashes Foundation 1.5.x, and maxLines alone still measures the
// whole paragraph, so huge strings are cut before layout as well as capped with maxLines.
internal const val TEXT_LOG_PREVIEW_MAX_LINES = 10
internal const val URL_PREVIEW_MAX_LINES = 3
internal const val ROW_PREVIEW_MAX_CHARS = 2_000

internal fun String.toRowPreview(maxChars: Int = ROW_PREVIEW_MAX_CHARS): String {
    if (length <= maxChars) return this
    // Don't split a surrogate pair at the cut point.
    val end = if (this[maxChars - 1].isHighSurrogate()) maxChars - 1 else maxChars
    return substring(0, end) + "…"
}
