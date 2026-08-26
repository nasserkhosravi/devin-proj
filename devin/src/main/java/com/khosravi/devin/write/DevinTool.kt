package com.khosravi.devin.write

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.khosravi.devin.api.DevinImageLogger
import com.khosravi.devin.write.api.DevinLogCore
import com.khosravi.devin.api.DevinLogger
import com.khosravi.devin.read.DevinUriHelper
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception

class DevinTool private constructor(
    val logger: DevinLogger?,
    val imageLogger: DevinImageLogger?,
    private val logCore: DevinLogCore? = null,
) {

    private fun putClient(appContext: Context, packageName: String, presenterConfig: JSONObject?) {
        appContext.contentResolver.insert(
            DevinUriHelper.getClientListUri(),
            DevinContentProvider.contentValuePutClient(packageName, presenterConfig)
        )
    }

    /**
     * Give available [DevinLogCore] instance.
     */
    fun connectPlugin(action: (logCore: DevinLogCore) -> Unit) {
        logCore?.let { action.invoke(it) }
    }

    /**
     * Builds the [JSONObject] passed as `presenterConfig` to [DevinTool.init], without hand-nesting
     * [JSONObject]/[JSONArray] calls. Keep this class's public API (method names/signatures)
     * identical to the `devin-no-op` module's version so call sites compile unchanged across
     * build variants - the no-op module's bodies are empty, like every other method there.
     */
    class PresenterConfigBuilder {
        private var logPassword: String? = null
        private val whitelistGroups = mutableListOf<GroupSpec>()
        private var uncategorizedGroup: GroupSpec? = null

        fun logPassword(password: String) = apply {
            this.logPassword = password
        }

        /**
         * Adds a notification group restricted to specific tags, titled with [name]: the
         * presenter posts a notification whenever a log whose tag is one of [tags] arrives.
         * Call this once per group, e.g. `putNotificationWhitelistGroup("Errors", "crash",
         * "http_5xx")` and `putNotificationWhitelistGroup("Network", "dvn_okhttp")`.
         *
         * A log's tag is checked against every whitelist group in the order this method was
         * called - the first group whose tags contain it wins, so listing the same tag in two
         * groups only ever notifies under the first one. A tag not listed in any whitelist
         * group, and not covered by [setNotificationUncategorizedGroup], produces no
         * notification - that's the default for every tag you don't mention here.
         *
         * [color], if given, is a `"#RRGGBB"`/`"#AARRGGBB"` hex string tinting that group's
         * notification (e.g. `color = "#FF0000"` for red) - an invalid hex string is ignored
         * (no tint), not an error.
         */
        fun putNotificationWhitelistGroup(name: String, vararg tags: String, color: String? = null) = apply {
            whitelistGroups.add(GroupSpec(name, tags.toList(), color))
        }

        /**
         * Sets the single catch-all notification group, titled with [name], for tags not
         * claimed by any [putNotificationWhitelistGroup] group, e.g.
         * `setNotificationUncategorizedGroup("Others")`. Calling this again replaces the
         * previous uncategorized group rather than adding another one - only one catch-all
         * makes sense.
         *
         * This group is always checked last, after every whitelist group, regardless of call
         * order relative to [putNotificationWhitelistGroup] - it never "steals" a tag a
         * whitelist group would have claimed. Don't call this if you don't want a catch-all;
         * tags outside every whitelist group will then simply produce no notification.
         *
         * [color] works the same as on [putNotificationWhitelistGroup].
         */
        fun setNotificationUncategorizedGroup(name: String, color: String? = null) = apply {
            uncategorizedGroup = GroupSpec(name, listOf(WILDCARD_TAG), color)
        }

        fun build(): JSONObject {
            val json = JSONObject()
            logPassword?.let { json.put(KEY_LOG_PASSWORD, it) }
            addNotificationGroups(json)
            return json
        }

        private fun addNotificationGroups(json: JSONObject) {
            if (whitelistGroups.isNotEmpty() || uncategorizedGroup != null) {
                val groups = JSONArray()
                whitelistGroups.forEach { groups.put(it.toJson()) }
                uncategorizedGroup?.let { groups.put(it.toJson()) }
                json.put(
                    KEY_LOG_NOTIFICATIONS,
                    JSONObject().put(KEY_ENABLED, true).put(KEY_GROUPS, groups)
                )
            }
        }

        private fun GroupSpec.toJson(): JSONObject = JSONObject()
            .put(KEY_NAME, name)
            .put(KEY_TAGS, JSONArray(tags))
            .apply { color?.let { put(KEY_COLOR, it) } }

        private data class GroupSpec(
            val name: String,
            val tags: List<String>,
            val color: String?,
        )

        private companion object {
            const val KEY_LOG_PASSWORD = "logPassword"
            const val KEY_LOG_NOTIFICATIONS = "logNotifications"
            const val KEY_ENABLED = "enabled"
            const val KEY_GROUPS = "groups"
            const val KEY_NAME = "name"
            const val KEY_TAGS = "tags"
            const val KEY_COLOR = "color"
            const val WILDCARD_TAG = "*"
        }
    }

    companion object {
        //sync API to no-op version
        private const val TAG = "DevinTool"

        private var instance: DevinTool? = null


        private fun create(appContext: Context, isEnable: Boolean, presenterConfig: JSONObject?): DevinTool {
            val packageName = appContext.packageName
            val devinTool = if (isEnable) {
                val logCore = LogCore(appContext, true)
                DevinTool(LoggerImpl(logCore), DevinImageLoggerImpl(logCore), logCore)
            } else DevinTool(null, null, null)

            if (!isEnable) {
                disableComponent(appContext, packageName, DevinContentProvider::class.java.name)
            } else {
                try {
                    devinTool.putClient(appContext, packageName, presenterConfig)
                } catch (e: Exception) {
                    Log.e(TAG, "No Devin receiver found. Please ensure a devin presenter application is installed.")
                    e.printStackTrace()
                    return DevinTool(null, null, null)
                }
            }
            return devinTool
        }

        fun get(): DevinTool? = instance

        fun init(context: Context) {
            if (instance == null) {
                init(context, null)
            }
        }

        fun init(context: Context, isEnable: Boolean? = null, presenterConfig: JSONObject? = null) {
            if (instance == null) {
                val fIsEnable: Boolean = isEnable ?: context.isDebuggable()
                instance = create(context, fIsEnable, presenterConfig)
            }
        }

        private fun Context.isDebuggable() = ((applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)

        private fun disableComponent(context: Context, packageName: String, componentClassName: String) {
            try {
                val componentName = ComponentName(packageName, componentClassName)
                context.applicationContext.packageManager.setComponentEnabledSetting(
                    componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: kotlin.Exception) {
                Log.e(TAG, "Error in disabling $componentClassName")
            }

        }
    }
}