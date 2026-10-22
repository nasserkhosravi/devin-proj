package com.khosravi.devin.write.okhttp.network.support

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Masks sensitive values in captured HTTP traffic before it is stored: header values, query parameters,
 * JSON and form fields, and text that matches a pattern (e.g. an email address).
 *
 * A masked value becomes `[REDACTED#a3f9]`: the suffix is a short fingerprint of the original value, so two
 * calls that sent the same value can be matched without revealing it.
 *
 * Default rules are on unless [defaultsEnabled] is turned off; rules added by the app always apply.
 *
 * It runs inside the app on every captured call, so the hot path avoids work per call: the rules are merged
 * once when they change, the verdict for each field name is cached, JSON is edited in place, and text is only
 * run through regexes when a cheap character check says a pattern could match.
 */
internal class Redactor {

    /** Rules merged from the defaults and the app's additions. Rebuilt only when the configuration changes. */
    private class Rules(
        val headers: Set<String>,
        val fieldNames: Set<String>,
        val useDefaults: Boolean,
        val extraPatterns: List<Regex>,
    )

    private var defaults = true
    private var extraHeaders: Set<String> = emptySet()
    private var extraFields: Set<String> = emptySet()
    private var extraPatterns: List<Regex> = emptyList()

    @Volatile
    private var rules: Rules = buildRules()

    /** Field name -> sensitive? JSON keys repeat across calls, so this skips splitting names into words. */
    private val fieldVerdicts = ConcurrentHashMap<String, Boolean>()

    var defaultsEnabled: Boolean
        get() = rules.useDefaults
        @Synchronized set(value) {
            defaults = value
            rebuild()
        }

    @Synchronized
    fun addHeaders(names: Collection<String>) {
        extraHeaders = extraHeaders + names.map { it.lowercase() }
        rebuild()
    }

    @Synchronized
    fun addFields(names: Collection<String>) {
        extraFields = extraFields + names.map(::normalize)
        rebuild()
    }

    @Synchronized
    fun addPatterns(patterns: Collection<Regex>) {
        extraPatterns = extraPatterns + patterns
        rebuild()
    }

    private fun rebuild() {
        rules = buildRules()
        fieldVerdicts.clear()
    }

    private fun buildRules() = Rules(
        headers = if (defaults) DEFAULT_HEADERS + extraHeaders else extraHeaders,
        fieldNames = if (defaults) DEFAULT_FIELD_NAMES + extraFields else extraFields,
        useDefaults = defaults,
        extraPatterns = extraPatterns,
    )

    fun redactHeaders(headers: Headers): Headers {
        val sensitive = rules.headers
        if (sensitive.isEmpty()) return headers
        var builder: Headers.Builder? = null
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            if (name.lowercase() in sensitive) {
                if (builder == null) builder = headers.newBuilder()
                builder[name] = mask(headers.values(name).joinToString(","))
            }
        }
        return builder?.build() ?: headers
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
        val current = rules
        var result = text
        if (current.useDefaults && mayContainDefaultPattern(result)) {
            result = DEFAULT_PATTERN.replace(result) { mask(it.value) }
        }
        for (pattern in current.extraPatterns) {
            result = pattern.replace(result) { mask(it.value) }
        }
        if (result.indexOf('=') >= 0) {
            result = QUERY_PAIR.replace(result) { match ->
                val (prefix, name, value) = match.destructured
                if (isSensitiveField(name)) "$prefix$name=${mask(value)}" else match.value
            }
        }
        return result
    }

    /**
     * Masks sensitive fields and matching text in a parsed JSON body, in place (the tree is the caller's own
     * freshly parsed copy). Returns the same element, or a replacement for a top-level string.
     */
    fun redactJson(element: JsonElement): JsonElement {
        when {
            element.isJsonObject -> for (entry in element.asJsonObject.entrySet()) {
                val value = entry.value
                if (isSensitiveField(entry.key)) {
                    maskJson(value)?.let { entry.setValue(it) }
                } else {
                    redactJsonChild(value)?.let { entry.setValue(it) }
                }
            }
            element.isJsonArray -> {
                val array: JsonArray = element.asJsonArray
                for (i in 0 until array.size()) {
                    redactJsonChild(array[i])?.let { array[i] = it }
                }
            }
            else -> return redactJsonChild(element) ?: element
        }
        return element
    }

    /** Redacts a child in place; returns a replacement only when the child is a string that changed. */
    private fun redactJsonChild(value: JsonElement): JsonElement? {
        if (value.isJsonObject || value.isJsonArray) {
            redactJson(value)
            return null
        }
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            val text = value.asString
            val redacted = redactText(text)
            return if (redacted !== text && redacted != text) JsonPrimitive(redacted) else null
        }
        return null
    }

    /** A sensitive key masks its whole value, unless it is null or a boolean (those reveal nothing). */
    private fun maskJson(value: JsonElement): JsonElement? {
        if (value.isJsonNull) return null
        if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) return null
        val raw = if (value.isJsonPrimitive) value.asString else value.toString()
        return JsonPrimitive(mask(raw))
    }

    private fun redactForm(body: String): String = body.split("&").joinToString("&") { pair ->
        val name = pair.substringBefore("=")
        if (pair.contains("=") && isSensitiveField(name)) "$name=${mask(pair.substringAfter("="))}" else pair
    }

    private fun isSensitiveField(name: String): Boolean {
        fieldVerdicts[name]?.let { return it }
        val current = rules
        val verdict = normalize(name) in current.fieldNames || (current.useDefaults && hasSensitiveWord(name))
        // Keys are usually a fixed vocabulary; a cap guards against APIs that use ids as keys.
        if (fieldVerdicts.size >= MAX_CACHED_FIELDS) fieldVerdicts.clear()
        fieldVerdicts[name] = verdict
        return verdict
    }

    companion object {
        private const val MASK = "[REDACTED"
        private const val MAX_CACHED_FIELDS = 2048

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

        /**
         * Emails, international phone numbers (+ and 10–15 digits), Iranian mobile numbers (09xxxxxxxxx) and
         * bearer tokens, in one regex so text is scanned once.
         */
        private val DEFAULT_PATTERN = Regex(
            listOf(
                """[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""",
                """(?<![\w+])\+\d{10,15}(?!\d)""",
                """(?<!\d)09\d{9}(?!\d)""",
                """(?<=(?i:bearer) )[A-Za-z0-9._~+/=-]{8,}""",
            ).joinToString("|")
        )

        /** `name=value` inside a URL or form-like text. */
        private val QUERY_PAIR = Regex("""([?&;\s]|^)([A-Za-z0-9_.-]+)=([^&\s#"']*)""")

        /**
         * Whether [text] has a character that [DEFAULT_PATTERN] needs: `@` for an email, `+` or `09` for a phone
         * number, or `bearer`. One pass without allocation; most values (names, ids, dates) fail it, so the regex
         * never runs on them.
         */
        private fun mayContainDefaultPattern(text: String): Boolean {
            for (i in text.indices) {
                when (text[i]) {
                    '@', '+' -> return true
                    '0' -> if (i + 1 < text.length && text[i + 1] == '9') return true
                    'b', 'B' -> if (text.regionMatches(i, "bearer ", 0, 7, ignoreCase = true)) return true
                }
            }
            return false
        }

        /** Splits `refreshToken`, `refresh_token` or `refresh-token` into words, without regex or allocation per char. */
        private fun hasSensitiveWord(name: String): Boolean {
            var start = 0
            for (i in 1..name.length) {
                val end = i == name.length
                val boundary = end || !name[i].isLetterOrDigit() ||
                    (name[i].isUpperCase() && (name[i - 1].isLowerCase() || name[i - 1].isDigit()))
                if (boundary) {
                    if (i > start && name.substring(start, i).lowercase() in DEFAULT_FIELD_WORDS) return true
                    start = if (!end && !name[i].isLetterOrDigit()) i + 1 else i
                }
            }
            return false
        }

        private fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

        private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
        private val HEX = "0123456789abcdef".toCharArray()

        /** `[REDACTED#a3f9]`: 4 hex characters of the value's SHA-256, too short to recover the value. */
        fun mask(value: String): String {
            val hash = digest.get()!!.digest(value.toByteArray())
            val out = CharArray(4)
            for (i in 0 until 2) {
                val b = hash[i].toInt() and 0xff
                out[i * 2] = HEX[b ushr 4]
                out[i * 2 + 1] = HEX[b and 0x0f]
            }
            return "$MASK#${String(out)}]"
        }
    }
}
