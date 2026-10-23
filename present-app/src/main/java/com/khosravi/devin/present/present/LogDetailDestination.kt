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
import com.khosravi.devin.present.present.stacktrace.extractStackTrace

/**
 * Registers the `logDetail` destination ([LogItemDetailScreen]) in a NavHost. Shared by [LogActivity]
 * and [ImportLogActivity]; open it with [LogDetailArgs.navigate].
 *
 * [appPackage] is the client's package, used to highlight its own frames in a stack trace; null when unknown
 * (e.g. imported logs).
 *
 * Back pops to [startRoute] rather than "pop the top": a second back tap during the exit transition
 * would otherwise pop the start destination too and leave an empty NavHost.
 */
fun NavGraphBuilder.logDetailDestination(
    navController: NavHostController,
    calendar: CalendarProxy,
    startRoute: String,
    appPackage: String?,
) {
    composable(LogDetailArgs.ROUTE_PATTERN, arguments = LogDetailArgs.arguments) { entry ->
        val context = LocalContext.current
        val args = remember(entry) { LogDetailArgs.read(entry) }
        val timeText = remember(args.timestamp) {
            calendar.getFormattedWithMillis(TimePresent(args.timestamp))
        }
        val split = remember(args.meta) { extractStackTrace(args.meta) }
        LogItemDetailScreen(
            tag = args.tag,
            message = args.message,
            timeText = timeText,
            // With a parsed stack trace, the meta shows without it; otherwise as-is (stack trace and all).
            meta = if (split != null) split.meta else args.meta,
            stackTrace = split?.stackTrace,
            appPackage = appPackage,
            onCopyStackTrace = { split?.let { context.copyStackTrace(it.stackTrace.raw) } },
            onCopy = { context.copyLogDetail(args.tag, timeText, args.message, args.meta) },
            onBack = { navController.popBackStack(startRoute, inclusive = false) },
        )
    }
}

/** True when the current entry is RESUMED, i.e. no navigation transition is running. Guards double taps. */
internal fun NavHostController.isCurrentEntryResumed(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
