package com.khosravi.devin.write

import android.content.Context
import android.net.Uri
import org.json.JSONObject

internal class LogCore(
    private val appContext: Context,
    val isEnable: Boolean
) {

    private val appId = appContext.packageName

    fun sendLog(tag: String?, value: String, meta: JSONObject? = null) {
        if (isEnable.not()) return

        val fTag = if (tag.isNullOrEmpty()) LoggerImpl.LOG_TAG_UNTAG else tag
        appContext.contentResolver.insert(
            Uri.parse(DevinContentProvider.URI_ALL_LOG),
            DevinContentProvider.contentValueLog(appId, fTag, value, meta?.toString())
        )
    }
}