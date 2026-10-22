package com.khosravi.devin.present.present

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.date.getFormattedWithMillis

/**
 * Registers the `logDetail` destination ([LogItemDetailScreen]) in a NavHost. Shared by [LogActivity]
 * and [ImportLogActivity]; open it with [LogDetailArgs.navigate].
 *
 * Back pops to [startRoute] rather than "pop the top": a second back tap during the exit transition
 * would otherwise pop the start destination too and leave an empty NavHost.
 */
fun NavGraphBuilder.logDetailDestination(navController: NavHostController, calendar: CalendarProxy, startRoute: String) {
    composable(LogDetailArgs.ROUTE_PATTERN, arguments = LogDetailArgs.arguments) { entry ->
        val context = LocalContext.current
        val args = remember(entry) { LogDetailArgs.read(entry) }
        val timeText = remember(args.timestamp) {
            calendar.getFormattedWithMillis(TimePresent(args.timestamp))
        }
        LogItemDetailScreen(
            tag = args.tag,
            message = args.message,
            timeText = timeText,
            meta = args.meta,
            onCopy = { context.copyLogDetail(args.tag, timeText, args.message, args.meta) },
            onBack = { navController.popBackStack(startRoute, inclusive = false) },
        )
    }
}

/** True when the current entry is RESUMED, i.e. no navigation transition is running. Guards double taps. */
internal fun NavHostController.isCurrentEntryResumed(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
