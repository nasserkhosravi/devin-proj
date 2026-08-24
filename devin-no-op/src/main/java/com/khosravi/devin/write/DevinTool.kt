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
         * Adds a named notification group. A log is only notified once, under the first group
         * (in the order added) whose [tags] contains that log's tag - tags not listed in any
         * group produce no notification.
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