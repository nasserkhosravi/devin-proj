package com.khosravi.devin.present.client

data class LogNotificationConfig(
    val isEnabled: Boolean,
    val allowedTags: Set<String>?,
) {
    fun includes(tag: String): Boolean = isEnabled && (allowedTags == null || tag in allowedTags)

    companion object {
        val DISABLED = LogNotificationConfig(false, emptySet())
    }
}

fun ClientData.getLogPassword(): String? {
    val string = presenterConfig?.optString("logPassword")
    if (!string.isNullOrEmpty()){
        return string
    }
    return null
}

fun ClientData.getLogNotificationConfig(): LogNotificationConfig {
    val config = presenterConfig?.optJSONObject(KEY_LOG_NOTIFICATIONS) ?: return LogNotificationConfig.DISABLED
    if (!config.optBoolean(KEY_ENABLED, false)) return LogNotificationConfig.DISABLED
    if (!config.has(KEY_TAGS)) return LogNotificationConfig(true, null)

    val jsonTags = config.optJSONArray(KEY_TAGS) ?: return LogNotificationConfig.DISABLED
    val tags = LinkedHashSet<String>(jsonTags.length())
    repeat(jsonTags.length()) { index ->
        val tag = jsonTags.opt(index) as? String ?: return LogNotificationConfig.DISABLED
        tags.add(tag)
    }
    return LogNotificationConfig(true, tags)
}

private const val KEY_LOG_NOTIFICATIONS = "logNotifications"
private const val KEY_ENABLED = "enabled"
private const val KEY_TAGS = "tags"
