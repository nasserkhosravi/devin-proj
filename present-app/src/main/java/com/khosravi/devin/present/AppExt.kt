package com.khosravi.devin.present

import android.content.Context
import android.util.Log
import com.khosravi.devin.present.log.TextLogItemData

const val KEY_DATA = "key_data"

fun TextLogItemData.getLogColor(context: Context): Int {
    return when (logLevel) {
        Log.DEBUG -> context.getColor(R.color.colorLogDebug)
        Log.ERROR -> context.getColor(R.color.colorLogError)
        Log.INFO -> context.getColor(R.color.colorLogInfo)
        Log.WARN -> context.getColor(R.color.colorLogWarning)
        else -> context.getColor(R.color.colorOnSurface)
    }
}