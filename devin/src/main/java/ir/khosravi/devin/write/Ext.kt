package ir.khosravi.devin.write

import android.content.Context

fun devinLogger(appContext: Context): DevinLogger = LoggerImpl(appContext.applicationContext)