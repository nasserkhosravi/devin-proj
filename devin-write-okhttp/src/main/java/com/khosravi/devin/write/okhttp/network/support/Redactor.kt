package com.khosravi.devin.write.okhttp.network.support

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import java.security.MessageDigest

/**
 * Masks sensitive values in captured HTTP traffic before it is stored: header values, query parameters,
 * JSON and form fields, and text that matches a pattern (e.g. an email address).
 *
 * A masked value becomes `[REDACTED#a3f9]`: the suffix is a short fingerprint of the original value, so two
 * calls that sent the same token can be matched without revealing it.
 *
 * Default rules are on unless [defaultsEnabled] is turned off; rules added by the app always apply.
 */
internal class Redactor {

    @Volatile
    var defaultsEnabled: Boolean = true

    @Volatile
    private var extraHeaders: Set<String> = emptySet()

    @Volatile
    private var extraFields: Set<String> = emptySet()

    @Volatile
    private var extraPatterns: List<Regex> = emptyList()

    @Synchronized
    fun addHeaders(names: Collection<String>) {
        extraHeaders = extraHeaders + names.map { it.lowercase() }
    }

    @Synchronized
    fun addFields(names: Collection<String>) {
        extraFields = extraFields + names.map(::normalize)
    }

    @Synchronized
    fun addPatterns(patterns: Collection<Regex>) {
        extraPatterns = extraPatterns + patterns
    }

    fun redactHeaders(headers: Headers): Headers {
        val builder = headers.newBuilder()
        for (name in headers.names()) {
            if (isSensitiveHeader(name)) {
                builder[name] = mask(headers.values(name).joinToString(","))
            }
        }
        return builder.build()
    }

    /** Masks the values of sensitive query parameters, keeping their order. */
    fun redactUrl(url: HttpUrl): HttpUrl {
        if (url.querySize == 0) return url
        if ((0 until url.querySize).none { isSensitiveField(url.queryParameterName(it)) }) return url
        val builder = url.newBuilder().query(null)
        for (i in 0 until url.querySize) {
            val name = url.queryParameterName(i)
            val value = url.queryParameterValue(i)
            builder.addQueryParameter(name, if (value != null && isSensitiveField(name)) mask(value) else value)
        }
        return builder.build()
    }

    /** Masks a body that isn't parsed as JSON: form fields, or text that matches a pattern. */
    fun redactBody(body: String?, contentType: String?): String? {
        if (body.isNullOrEmpty()) return body
        if (contentType?.contains("x-www-form-urlencoded") == true) {
            return redactForm(body)
        }
        return redactText(body)
    }

    /** Masks matching patterns, and sensitive `name=value` pairs (e.g. a URL inside an error message). */
    fun redactText(text: String): String {
        var result = text
        for (pattern in patterns()) {
            result = pattern.replace(result) { mask(it.value) }
        }
        return QUERY_PAIR.replace(result) { match ->
            val (prefix, name, value) = match.destructured
            if (isSensitiveField(name)) "$prefix$name=${mask(value)}" else match.value
        }
    }

    /** Masks sensitive fields and matching text in a parsed JSON body. */
    fun redactJson(element: JsonElement): JsonElement = when {
        element.isJsonObject -> {
            val result = JsonObject()
            for ((key, value) in element.asJsonObject.entrySet()) {
                result.add(key, if (isSensitiveField(key)) maskJson(value) else redactJson(value))
            }
            result
        }
        element.isJsonArray -> JsonArray().apply { element.asJsonArray.forEach { add(redactJson(it)) } }
        element.isJsonPrimitive && element.asJsonPrimitive.isString -> JsonPrimitive(redactText(element.asString))
        else -> element
    }

    /** A sensitive key masks its whole value, unless it is null or a boolean (those reveal nothing). */
    private fun maskJson(value: JsonElement): JsonElement {
        if (value.isJsonNull) return value
        if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) return value
        val raw = if (value.isJsonPrimitive) value.asString else value.toString()
        return JsonPrimitive(mask(raw))
    }

    private fun redactForm(body: String): String = body.split("&").joinToString("&") { pair ->
        val name = pair.substringBefore("=")
        if (pair.contains("=") && isSensitiveField(name)) "$name=${mask(pair.substringAfter("="))}" else pair
    }

    private fun isSensitiveHeader(name: String): Boolean {
        val lower = name.lowercase()
        return lower in extraHeaders || (defaultsEnabled && lower in DEFAULT_HEADERS)
    }

    private fun isSensitiveField(name: String): Boolean {
        val normalized = normalize(name)
        if (normalized in extraFields) return true
        if (!defaultsEnabled) return false
        if (normalized in DEFAULT_FIELD_NAMES) return true
        return words(name).any { it in DEFAULT_FIELD_WORDS }
    }

    private fun patterns(): List<Regex> = if (defaultsEnabled) DEFAULT_PATTERNS + extraPatterns else extraPatterns

    companion object {
        private const val MASK = "[REDACTED"

        /** Headers whose values are credentials. */
        private val DEFAULT_HEADERS = setOf(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "x-api-key", "x-auth-token", "x-access-token",
        )

        /** A field is sensitive when one of the words in its name is one of these (`refreshToken`, `user_phone`). */
        private val DEFAULT_FIELD_WORDS = setOf(
            "token", "password", "passwd", "secret", "authorization", "cookie",
            "phone", "cellphone", "mobile", "msisdn", "email", "otp", "pin",
        )

        /** Whole field names that are sensitive but aren't single words. */
        private val DEFAULT_FIELD_NAMES = setOf("apikey", "accesskey", "privatekey", "nationalid", "nationalcode")

        private val DEFAULT_PATTERNS = listOf(
            // Email addresses.
            Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""),
            // International phone numbers (+ and 10–15 digits) and Iranian mobile numbers (09xxxxxxxxx).
            Regex("""(?<![\w+])\+\d{10,15}(?!\d)"""),
            Regex("""(?<!\d)09\d{9}(?!\d)"""),
            // Bearer tokens in text.
            Regex("""(?i)(?<=bearer )[A-Za-z0-9._~+/=-]{8,}"""),
        )

        /** `name=value` inside a URL or form-like text. */
        private val QUERY_PAIR = Regex("""([?&;\s]|^)([A-Za-z0-9_.-]+)=([^&\s#"']*)""")

        private val WORD_BOUNDARY = Regex("""[^A-Za-z0-9]+|(?<=[a-z0-9])(?=[A-Z])""")

        private fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

        private fun words(name: String): List<String> =
            name.split(WORD_BOUNDARY).filter { it.isNotEmpty() }.map { it.lowercase() }

        /** `[REDACTED#a3f9]`: 4 hex characters of the value's SHA-256, too short to recover the value. */
        fun mask(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            val fingerprint = digest.take(2).joinToString("") { "%02x".format(it) }
            return "$MASK#$fingerprint]"
        }
    }
}
