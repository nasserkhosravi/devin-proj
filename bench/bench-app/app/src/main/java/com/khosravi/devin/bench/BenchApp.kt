package com.khosravi.devin.bench

import android.app.Application
import com.khosravi.devin.write.DevinTool

class BenchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DevinTool.init(this)
        DevinTool.get()?.logger?.let {
            it.generalUncaughtExceptionLogging(true)
            it.logSessionStart(this)
        }
        AppLog.i("App", "BenchApp started")
    }
}
