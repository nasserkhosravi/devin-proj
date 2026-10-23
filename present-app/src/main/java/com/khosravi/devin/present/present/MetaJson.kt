package com.khosravi.devin.present.present

import androidx.compose.ui.text.AnnotatedString
import com.google.gson.JsonParser
import com.khosravi.devin.present.present.http.JsonConfigColor
import com.khosravi.devin.present.present.http.JsonSpanTextUtil
import com.khosravi.devin.present.present.http.toAnnotatedLines
import com.khosravi.devin.present.uikit.text.MAX_CHARS_PER_TEXT

/**
 * [meta] pretty-printed and syntax-coloured (same colours as the HTTP body), one entry per display row; long lines
 * are split so no single Text gets too tall to measure. Null when [meta] isn't a JSON object or array.
 */
internal fun formatMetaJson(meta: String, colors: JsonConfigColor): List<AnnotatedString>? {
    val json = runCatching { JsonParser.parseString(meta) }.getOrNull()
    if (json == null || !(json.isJsonObject || json.isJsonArray)) return null
    return JsonSpanTextUtil(colors).spanJson(meta).toAnnotatedLines().flatMap { line ->
        val text = line.text
        (0 until text.length step MAX_CHARS_PER_TEXT)
            .map { text.subSequence(it, minOf(it + MAX_CHARS_PER_TEXT, text.length)) }
            .ifEmpty { listOf(text) }
    }
}
