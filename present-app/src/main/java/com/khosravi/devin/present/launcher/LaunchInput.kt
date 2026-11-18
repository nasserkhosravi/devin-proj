package com.khosravi.devin.present.launcher

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/** What a launch sends: a deeplink and, optionally, one String extra whose value is a JSON object. */
data class LaunchInput(val deeplink: String, val extraKey: String?, val extraValue: String?) {

    companion object {
        /**
         * Trims the fields. A blank value means "no extra", and then the key is dropped too: the key field is
         * pre-filled, so a deeplink-only launch must not need it cleared first.
         */
        fun of(deeplink: String, extraKey: String, extraValue: String): LaunchInput {
            val value = extraValue.trim().ifEmpty { null }
            return LaunchInput(
                deeplink = deeplink.trim(),
                extraKey = if (value == null) null else extraKey.trim().ifEmpty { null },
                extraValue = value,
            )
        }
    }
}

enum class LaunchInputError { MissingDeeplink, InvalidDeeplink, MissingKey, InvalidJson }

private val SCHEME_REGEX = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*:""")

/** The first rule the fields break, or null when they can be launched. A value needs a key; a key alone is ignored. */
fun validateLaunchInput(deeplink: String, extraKey: String, extraValue: String): LaunchInputError? {
    val input = LaunchInput.of(deeplink, extraKey, extraValue)
    return when {
        input.deeplink.isEmpty() -> LaunchInputError.MissingDeeplink
        !SCHEME_REGEX.containsMatchIn(input.deeplink) -> LaunchInputError.InvalidDeeplink
        input.extraKey == null && input.extraValue != null -> LaunchInputError.MissingKey
        input.extraValue != null && parseJsonObject(input.extraValue) == null -> LaunchInputError.InvalidJson
        else -> null
    }
}

/** Two launches with the same identity are the same saved entry; JSON whitespace doesn't count. */
fun LaunchInput.identity(): String {
    val json = extraValue?.let { parseJsonObject(it)?.toString() ?: it }.orEmpty()
    return "$deeplink\u0000${extraKey.orEmpty()}\u0000$json"
}

/** [text] as a JSON object, or null when it is anything else (an array, a plain value, or text after the object). */
private fun parseJsonObject(text: String): JSONObject? = try {
    val tokener = JSONTokener(text)
    val value = tokener.nextValue()
    if (value is JSONObject && tokener.nextClean() == 0.toChar()) value else null
} catch (_: JSONException) {
    null
}
