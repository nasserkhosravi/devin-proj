package com.khosravi.devin.write.api

import android.net.Uri

interface DevinLogCore {

    companion object {
        const val FLAG_OPERATION_FAILED = -1
        const val FLAG_OPERATION_SUCCESS = 1
    }

    fun insertLog(
        tag: String?, value: String, typeId: String?,
        meta: String? = null, content: ByteArray? = null,
        metaIndex: Pair<String, String>? = null,
    ): Uri?

    fun updateLog(
        itemId: Uri,
        tag: String?,
        value: String,
        typeId: String,
        meta: String? = null,
        content: ByteArray? = null
    ): Int

    fun isEnable(): Boolean
}