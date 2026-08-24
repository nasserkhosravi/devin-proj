package com.khosravi.devin.write

import android.content.Context
import com.khosravi.devin.api.DevinImageLogger
import com.khosravi.devin.api.DevinLogger
import com.khosravi.devin.write.api.DevinLogCore
import org.json.JSONArray
import org.json.JSONObject

class DevinTool private constructor(
    val logger: DevinLogger?,
    val imageLogger: DevinImageLogger?,
    private val logCore: DevinLogCore? = null,
) {

    /**
     * Give available [DevinLogCore] instance.
     */
    fun connectPlugin(action: (logCore: DevinLogCore) -> Unit) {
        //no impl
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


        fun get(): DevinTool? = null

        fun init(context: Context) {
            //no impl
        }

        fun init(context: Context, isEnable: Boolean? = null, presenterConfig: JSONObject? = null) {
            //no impl
        }

    }
}