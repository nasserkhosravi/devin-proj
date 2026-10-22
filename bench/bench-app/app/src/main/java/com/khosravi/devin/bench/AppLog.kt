package com.khosravi.devin.bench

import android.util.Log
import com.khosravi.devin.write.DevinTool

/** Writes every app log to logcat and to Devin, so both see the same lines. */
object AppLog {

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        DevinTool.get()?.logger?.debug(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        DevinTool.get()?.logger?.info(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        DevinTool.get()?.logger?.warning(tag, message, throwable = throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        DevinTool.get()?.logger?.error(tag, message, throwable = throwable)
    }
}
