package com.khosravi.devin.present.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface LaunchResult {
    data object Success : LaunchResult
    data class Failure(val reason: Reason) : LaunchResult

    enum class Reason { NoHandler, NotExported }
}

/**
 * Opens [input]'s deeplink in the client [packageId] only (never a browser or another app), with its JSON as a
 * String extra when one is set. Fails when no exported activity of the client handles the link.
 */
fun launchClient(context: Context, packageId: String, input: LaunchInput): LaunchResult {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(input.deeplink)).setPackage(packageId)
    if (input.extraKey != null && input.extraValue != null) intent.putExtra(input.extraKey, input.extraValue)
    return try {
        context.startActivity(intent)
        LaunchResult.Success
    } catch (_: ActivityNotFoundException) {
        LaunchResult.Failure(LaunchResult.Reason.NoHandler)
    } catch (_: SecurityException) {
        // The matching activity isn't exported.
        LaunchResult.Failure(LaunchResult.Reason.NotExported)
    }
}
