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
     * [JSONObject]/[JSONArray] calls. Keep this class identical between the `devin` and `devin-no-op`
     * modules - it only builds JSON, it has no write/no-op behavior to diverge on.
     */
    class PresenterConfigBuilder {
        private val json = JSONObject()

        fun logPassword(password: String) = apply {
            json.put(KEY_LOG_PASSWORD, password)
        }

        /**
         * Adds a named notification group: the presenter posts a notification whenever a log
         * matching this group's [tags] arrives, titled with [name]. Not calling this method at
         * all means no notifications are ever posted - notifications are opt-in, there is no
         * group by default.
         *
         * What to pass depends on what you want notified:
         *
         * - **Specific tags only** ("Errors", "Network", ...) - pass one or more real tag
         *   values, e.g. `notificationGroup("Errors", "crash", "http_5xx")` or
         *   `notificationGroup("Network", "dvn_okhttp")`. Only logs whose tag is exactly one of
         *   the given values notify under this group.
         * - **Everything not otherwise categorized** ("Others") - pass the single wildcard tag
         *   `"*"`, e.g. `notificationGroup("Others", "*")`. Matches any log tag not claimed by
         *   another group's explicit tags. Only the first wildcard group (in call order) is ever
         *   used if you add more than one.
         * - **No notification for a given tag** - simply never list that tag in any group (and
         *   don't add a wildcard group either). This is the default for every tag you don't
         *   mention.
         *
         * Matching rule when multiple groups are configured: a log's tag is checked against
         * every specific-tags group first, in the order this method was called - the first one
         * that contains the tag wins. Only if none match does a wildcard group (if any) catch
         * it, regardless of where that wildcard group was added relative to the others - it
         * never "steals" a tag a specific-tags group would have claimed.
         */
        fun notificationGroup(name: String, vararg tags: String) = apply {
            val notifications = json.optJSONObject(KEY_LOG_NOTIFICATIONS) ?: JSONObject()
                .put(KEY_ENABLED, true)
                .also { json.put(KEY_LOG_NOTIFICATIONS, it) }

            val groups = notifications.optJSONArray(KEY_GROUPS) ?: JSONArray()
                .also { notifications.put(KEY_GROUPS, it) }

            groups.put(
                JSONObject()
                    .put(KEY_NAME, name)
                    .put(KEY_TAGS, JSONArray(tags.toList()))
            )
        }

        fun build(): JSONObject = json

        private companion object {
            const val KEY_LOG_PASSWORD = "logPassword"
            const val KEY_LOG_NOTIFICATIONS = "logNotifications"
            const val KEY_ENABLED = "enabled"
            const val KEY_GROUPS = "groups"
            const val KEY_NAME = "name"
            const val KEY_TAGS = "tags"
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
            val componentName = ComponentName(packageName, componentClassName)
            context.applicationContext.packageManager.setComponentEnabledSetting(
                componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}