package com.khosravi.devin.present.analytics

import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.IndexFilterItem
import io.appmetrica.analytics.AppMetrica

/**
 * Product analytics on top of AppMetrica. Reports nothing unless AppMetrica was activated
 * (release builds with an API key, see [com.khosravi.devin.present.crash.CrashReporting]).
 *
 * Never put client-provided content (tags, client ids, urls, search text, log content) in params.
 */
object Analytics {

    @Volatile
    var isEnabled: Boolean = false

    // region Clients
    fun clientListLoaded(clientCount: Int) = report(
        "client_list_loaded",
        "count" to when (clientCount) {
            0 -> "zero"
            1 -> "single"
            else -> "multi"
        }
    )

    fun clientOpened(source: ClientOpenSource, isPasswordProtected: Boolean) = report(
        "client_opened",
        "source" to source.value,
        "password_protected" to isPasswordProtected
    )

    fun clientPasswordResult(isCorrect: Boolean) = report("client_password_entered", "correct" to isCorrect)

    fun clientLockedOut() = report("client_locked_out")

    fun notificationPermissionResult(isGranted: Boolean) = report("notification_permission_result", "granted" to isGranted)
    // endregion

    // region Log list
    fun filterSelected(type: FilterType) = report("filter_selected", "type" to type.value)

    fun customFilterCreated(hasTag: Boolean, hasSearchText: Boolean) = report(
        "custom_filter_created",
        "has_tag" to hasTag,
        "has_search_text" to hasSearchText
    )

    fun customFilterRemoved() = report("custom_filter_removed")

    fun customFiltersCleared() = report("custom_filters_cleared")

    fun filterPinToggled(type: FilterType, isPinned: Boolean) = report(
        "filter_pin_toggled",
        "type" to type.value,
        "pinned" to isPinned
    )

    fun filterLogsShared() = report("filter_logs_shared")

    fun logsSearched(type: FilterType) = report("logs_searched", "filter_type" to type.value)

    fun logsRefreshed() = report("logs_refreshed")

    fun logsCleared() = report("logs_cleared")

    fun themeToggled(isDark: Boolean) = report("theme_toggled", "dark" to isDark)
    // endregion

    // region Log detail
    fun logDetailOpened(type: LogType, source: LogSource) = report(
        "log_detail_opened",
        "type" to type.value,
        "source" to source.value
    )

    fun textLogDetailCopied() = report("text_log_detail_copied")

    fun httpDetailAction(action: String) = report("http_detail_action", "action" to action)
    // endregion

    // region Export / import
    fun exportDialogOpened() = report("export_dialog_opened")

    fun logsExported(isDefaultOption: Boolean, destination: ExportDestination, isSuccessful: Boolean) = report(
        "logs_exported",
        "option" to if (isDefaultOption) "default" else "custom",
        "destination" to destination.value,
        "success" to isSuccessful
    )

    fun logsImported(isSuccessful: Boolean) = report("logs_imported", "success" to isSuccessful)
    // endregion

    // region Update
    fun updatePromptShown(isForceUpdate: Boolean) = report("update_prompt_shown", "force" to isForceUpdate)

    fun updatePromptAction(isForceUpdate: Boolean, action: UpdatePromptAction) = report(
        "update_prompt_action",
        "force" to isForceUpdate,
        "action" to action.value
    )

    fun updateFooterClicked(hasUpdate: Boolean) = report("update_footer_clicked", "has_update" to hasUpdate)

    fun updateNotificationPosted() = report("update_notification_posted")
    // endregion

    private fun report(name: String, vararg params: Pair<String, Any>) {
        if (!isEnabled) return
        if (params.isEmpty()) {
            AppMetrica.reportEvent(name)
        } else {
            AppMetrica.reportEvent(name, params.toMap())
        }
    }

    enum class ClientOpenSource(val value: String) { LIST("list"), AUTO("auto"), NOTIFICATION("notification") }

    enum class FilterType(val value: String) {
        INDEX("index"), TAG("tag"), CUSTOM("custom");

        companion object {
            fun of(item: FilterItem): FilterType = when (item) {
                is IndexFilterItem -> INDEX
                is CustomFilterItem -> CUSTOM
                else -> TAG
            }
        }
    }

    enum class LogType(val value: String) { TEXT("text"), HTTP("http") }

    enum class LogSource(val value: String) { LIVE("live"), IMPORTED("imported") }

    enum class ExportDestination(val value: String) { SAVE("save"), SHARE("share") }

    enum class UpdatePromptAction(val value: String) { DOWNLOAD("download"), LATER("later"), DISMISS("dismiss") }
}
