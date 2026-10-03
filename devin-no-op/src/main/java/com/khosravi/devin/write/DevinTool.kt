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
     * Builds the [JSONObject] passed as `presenterConfig` to [DevinTool.init]. Keep this class's
     * public API (method names/signatures) identical to the `devin` module's version so call
     * sites compile unchanged across build variants - the no-op bodies below stay empty like
     * every other method in this file, since no-op's [DevinTool.init] ignores presenterConfig.
     */
    class PresenterConfigBuilder {

        fun logPassword(password: String) = apply {
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
        }

        fun build(): JSONObject = JSONObject()

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