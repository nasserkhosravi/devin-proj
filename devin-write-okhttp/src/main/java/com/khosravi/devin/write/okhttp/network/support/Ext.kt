package com.khosravi.devin.write.okhttp.network.support

import com.khosravi.devin.write.okhttp.network.entity.HttpHeaderModel
import okhttp3.Headers
import okhttp3.Response
import okio.Buffer
import okio.ByteString
import java.io.EOFException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.min

private const val HTTP_CONTINUE = 100

/** Returns true if the response must have a (possibly 0-length) body. See RFC 7231.  */
internal fun Response.hasBody(): Boolean {
    // HEAD requests never yield a body regardless of the response headers.
    if (request.method == "HEAD") {
        return false
    }

    val responseCode = code
    if ((responseCode < HTTP_CONTINUE || responseCode >= HttpURLConnection.HTTP_OK) &&
        (responseCode != HttpURLConnection.HTTP_NO_CONTENT) &&
        (responseCode != HttpURLConnection.HTTP_NOT_MODIFIED)
    ) {
        return true
    }

    // If the Content-Length or Transfer-Encoding headers disagree with the response code, the
    // response is malformed. For best compatibility, we honor the headers.
    return ((contentLength > 0) || isChunked)
}

private val Response.contentLength: Long
    get() {
        return this.header("Content-Length")?.toLongOrNull() ?: -1L
    }

internal val Response.isChunked: Boolean
    get() {
        return this.header("Transfer-Encoding").equals("chunked", ignoreCase = true)
    }

internal val Response.contentType: String?
    get() {
        return this.header("Content-Type")
    }

private val supportedEncodings = listOf("identity", "gzip", "br")

internal val Headers.hasSupportedContentEncoding: Boolean
    get() =
        get("Content-Encoding")
            ?.takeIf { it.isNotEmpty() }
            ?.let { it.lowercase(Locale.ROOT) in supportedEncodings }
            ?: true

internal fun Headers.exclude(blacklist: Iterable<String>): Headers {
    val builder = newBuilder()
    for (name in names()) {
        if (blacklist.any { userHeader -> userHeader.equals(name, ignoreCase = true) }) {
            builder[name] = "**"
        }
    }
    return builder.build()
}

internal fun Headers.toMyHttpHeaderModelList(): List<HttpHeaderModel> {
    val size = size
    if (size == 0) return emptyList()
    val result = ArrayList<HttpHeaderModel>(size)
    for (i in 0 until size) {
        result.add(HttpHeaderModel(name(i), value(i)))
    }
    return result
}

internal fun URL.getQueryParameters(): List<Pair<String, String>> {
    val query = query ?: return emptyList()

    return query.split("&").mapNotNull { param ->
        val keyValue = param.split("=", limit = 2)
        if (keyValue.size == 2) {
            keyValue[0] to keyValue[1]
        } else {
            null // Skip malformed parameters
        }
    }
}


private const val MAX_PREFIX_SIZE = 64L
private const val CODE_POINT_SIZE = 16

/**
 * Returns true if the [Buffer] contains human readable text. Uses a small sample
 * of code points to detect unicode control characters commonly used in binary file signatures.
 */
internal val Buffer.isProbablyPlainText
    get() =
        try {
            val prefix = Buffer()
            val byteCount = min(size, MAX_PREFIX_SIZE)
            copyTo(prefix, 0, byteCount)
            sequence { while (!prefix.exhausted()) yield(prefix.readUtf8CodePoint()) }
                .take(CODE_POINT_SIZE)
                .all { codePoint -> codePoint.isPlainTextChar() }
        } catch (_: EOFException) {
            false // Truncated UTF-8 sequence
        }

internal val ByteString.isProbablyPlainText: Boolean
    get() {
        val byteCount = min(size, MAX_PREFIX_SIZE.toInt())
        return Buffer().write(this, offset = 0, byteCount).isProbablyPlainText
    }

private fun Int.isPlainTextChar() = Character.isWhitespace(this) || !Character.isISOControl(this)
