package com.khosravi.devin.present.present

import androidx.core.os.bundleOf
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.khosravi.devin.present.log.TextLogItemData

/**
 * Single owner of the `logDetail` navigation destination used by [LogActivity]'s NavHost.
 * Carries the same values [LogItemDetailActivity] receives as Intent extras.
 *
 * Values travel as a Bundle, navigating by destination id ([navigate]), not as a route URI:
 * navigation matches route URIs with a regex, which fails for very long messages (~50k chars).
 * [ROUTE_PATTERN] is kept only to register the destination and its [arguments].
 * Never navigate here with `navController.navigate(String)`: that path re-parses values from the URI
 * (and turns a literal "null" into a real null).
 */
object LogDetailArgs {
    private const val ROUTE_BASE = "logDetail"

    private const val ARG_TAG = "tag"
    private const val ARG_MESSAGE = "message"
    private const val ARG_TIMESTAMP = "timestamp"
    private const val ARG_META = "meta"

    const val ROUTE_PATTERN =
        "$ROUTE_BASE?$ARG_TAG={$ARG_TAG}&$ARG_MESSAGE={$ARG_MESSAGE}&$ARG_TIMESTAMP={$ARG_TIMESTAMP}&$ARG_META={$ARG_META}"

    val arguments: List<NamedNavArgument> = listOf(
        navArgument(ARG_TAG) { type = NavType.StringType; defaultValue = "" },
        navArgument(ARG_MESSAGE) { type = NavType.StringType; defaultValue = "" },
        navArgument(ARG_TIMESTAMP) { type = NavType.LongType },
        navArgument(ARG_META) { type = NavType.StringType; nullable = true; defaultValue = null },
    )

    fun navigate(navController: NavController, data: TextLogItemData) {
        val destination = requireNotNull(navController.graph.findNode(ROUTE_PATTERN)) {
            "logDetail destination is not registered in the nav graph"
        }
        navController.navigate(
            destination.id,
            bundleOf(
                ARG_TAG to data.tag,
                ARG_MESSAGE to data.text,
                ARG_TIMESTAMP to data.timePresent.timestamp,
                ARG_META to data.meta?.toString(),
            ),
        )
    }

    fun read(entry: NavBackStackEntry): Values {
        val args = requireNotNull(entry.arguments) { "logDetail destination opened without arguments" }
        return Values(
            tag = args.getString(ARG_TAG).orEmpty(),
            message = args.getString(ARG_MESSAGE).orEmpty(),
            timestamp = args.getLong(ARG_TIMESTAMP),
            meta = args.getString(ARG_META),
        )
    }

    data class Values(val tag: String, val message: String, val timestamp: Long, val meta: String?)
}
