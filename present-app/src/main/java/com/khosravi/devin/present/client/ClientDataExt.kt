package com.khosravi.devin.present.client

import android.util.Log

data class NotificationGroup(
    val name: String,
    val tags: Set<String>,
)

data class LogNotificationConfig(
    val isEnabled: Boolean,
    val groups: List<NotificationGroup>,
) {
    /**
     * First group (in [groups] order) whose `tags` literally contains [tag]. If none match,
     * falls back to the first group whose `tags` contains the wildcard `"*"`, regardless of
     * that group's position in [groups] - a wildcard group always acts as a catch-all, never
     * as a priority match.
     */
    fun groupFor(tag: String): NotificationGroup? {
        if (!isEnabled) return null
        groups.firstOrNull { tag in it.tags }?.let { return it }
        return groups.firstOrNull { WILDCARD_TAG in it.tags }
    }

    companion object {
        val DISABLED = LogNotificationConfig(false, emptyList())
    }
}

fun ClientData.getLogPassword(): String? {
    val string = presenterConfig?.optString("logPassword")
    if (!string.isNullOrEmpty()) {
        return string
    }
    return null
}

fun ClientData.getLogNotificationConfig(): LogNotificationConfig {
    val config = presenterConfig?.optJSONObject(KEY_LOG_NOTIFICATIONS) ?: return LogNotificationConfig.DISABLED
    if (!config.optBoolean(KEY_ENABLED, false)) return LogNotificationConfig.DISABLED

    val jsonGroups = config.optJSONArray(KEY_GROUPS)
    if (jsonGroups == null) {
        Log.w(TAG, "logNotifications.enabled is true but groups is missing")
        return LogNotificationConfig.DISABLED
    }

    val groups = mutableListOf<NotificationGroup>()
    repeat(jsonGroups.length()) { index ->
        val jsonGroup = jsonGroups.optJSONObject(index)
        if (jsonGroup == null) {
            Log.w(TAG, "Skipping non-object logNotifications.groups[$index]")
            return@repeat
        }
        val name = jsonGroup.optString(KEY_NAME).takeIf { it.isNotEmpty() }
        val jsonTags = jsonGroup.optJSONArray(KEY_TAGS)
        if (name == null || jsonTags == null) {
            Log.w(TAG, "Skipping malformed logNotifications.groups[$index]: missing name or tags")
            return@repeat
        }
        val tags = LinkedHashSet<String>(jsonTags.length())
        repeat(jsonTags.length()) { tagIndex ->
            (jsonTags.opt(tagIndex) as? String)?.let(tags::add)
        }
        if (tags.isEmpty()) {
            Log.w(TAG, "Skipping logNotifications.groups[$index] ('$name'): no valid string tags")
            return@repeat
        }
        groups.add(NotificationGroup(name, tags))
    }

    if (groups.isEmpty()) return LogNotificationConfig.DISABLED
    return LogNotificationConfig(true, groups)
}

private const val TAG = "ClientDataExt"
private const val KEY_LOG_NOTIFICATIONS = "logNotifications"
private const val KEY_ENABLED = "enabled"
private const val KEY_GROUPS = "groups"
private const val KEY_NAME = "name"
private const val KEY_TAGS = "tags"
private const val WILDCARD_TAG = "*"
