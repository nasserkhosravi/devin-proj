package com.khosravi.devin.write.okhttp

import android.util.Log

internal object InternalLogger {

    private const val TAG = "DevinInternal"

    fun debug(message: String, tag: String = TAG) {
        Log.d(tag, message)
    }

    fun info(message: String, tag: String = TAG) {
        Log.i(tag, message)
    }

    fun error(message: String, e: Exception?, tag: String = TAG) {
        Log.e(tag, message, e)
    }

    fun warn(message: String, e: Exception?, tag: String = TAG) {
        Log.e(tag, message, e)
    }
}
