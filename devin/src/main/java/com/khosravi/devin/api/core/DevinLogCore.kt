package com.khosravi.devin.api.core

import android.net.Uri
import org.json.JSONObject

interface DevinLogCore {

    companion object {
        const val FLAG_OPERATION_FAILED = -1
        const val FLAG_OPERATION_SUCCESS = 1
    }

    fun sendLog(tag: String?, value: String, meta: JSONObject? = null): Uri?

    fun updateLog(itemId: Uri, tag: String?, value: String, meta: JSONObject? = null): Int

    fun isEnable(): Boolean
}