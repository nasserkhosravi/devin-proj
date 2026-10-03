package com.khosravi.devin.present.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.R

/** Opens [url] in a browser; shows a short toast instead of crashing when no browser is installed. */
fun Context.openReleasePage(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, R.string.error_msg_something_went_wrong, Toast.LENGTH_SHORT).show()
    }
}

fun Context.updateTitle(update: ReleaseInfo): String =
    getString(if (update.isForceUpdate) R.string.update_required_title else R.string.update_available_title)

fun Context.updateMessage(update: ReleaseInfo): String = getString(
    if (update.isForceUpdate) R.string.update_required_message else R.string.update_available_message,
    update.version.toString(),
    BuildConfig.VERSION_NAME,
)
