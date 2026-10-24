package com.khosravi.devin.present.present

import android.content.Context
import android.widget.Toast
import com.khosravi.devin.present.R
import com.khosravi.devin.present.analytics.Analytics
import com.khosravi.devin.present.setClipboardSafe

/** Copies a log's detail as plain text and confirms with a toast. Shared by both detail hosts. */
fun Context.copyLogDetail(tag: String, timeText: String, message: String, meta: String?) {
    val content = buildString {
        appendLine("Tag: $tag")
        appendLine("Time: $timeText")
        appendLine("Message: $message")
        meta?.let { appendLine("Meta: $it") }
    }
    if (setClipboardSafe(content)) {
        Analytics.textLogDetailCopied()
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
}

/** Copies only the stack trace, unescaped, ready for Android Studio's Analyze Stack Trace or a ticket. */
fun Context.copyStackTrace(stackTrace: String) {
    if (setClipboardSafe(stackTrace)) {
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
}
