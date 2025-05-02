package com.khosravi.devin.write

import android.content.Context
import android.net.Uri
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.api.DevinLogCore

internal class LogCore(
    private val appContext: Context,
    private val isEnable: Boolean,
) : DevinLogCore {

    private val appId = appContext.packageName

    override fun insertLog(
        tag: String?,
        value: String,
        typeId: String?,
        meta: String?,
        content: ByteArray?,
        metaIndex: Pair<String, String>?
    ): Uri? {
        if (isEnable.not()) return null

        val fTag = if (tag.isNullOrEmpty()) LoggerImpl.LOG_TAG_UNTAG else tag

        return appContext.contentResolver.insert(
            DevinUriHelper.getLogListUri(),
            DevinContentProvider.contentValueLog(
                appId, fTag, value, typeId, meta, content, metaIndex
            )
        )
    }

    override fun updateLog(itemId: Uri, tag: String?, value: String, typeId: String, meta: String?, content: ByteArray?): Int {
        if (isEnable.not()) return DevinLogCore.FLAG_OPERATION_FAILED

        val fTag = if (tag.isNullOrEmpty()) LoggerImpl.LOG_TAG_UNTAG else tag
        return appContext.contentResolver.update(
            itemId,
            DevinContentProvider.contentValueLog(appId, fTag, value, typeId, meta, content, null), null, null
        )
    }

    override fun isEnable(): Boolean = this.isEnable
}