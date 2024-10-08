package com.khosravi.devin.write

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import io.nasser.devin.api.DevinImageLogger
import io.nasser.devin.api.DevinLogger
import java.lang.Exception

class DevinTool private constructor(
    val logger: DevinLogger?,
    val imageLogger: DevinImageLogger?
) {

    private fun putClient(appContext: Context, packageName: String) {
        try {
            appContext.contentResolver.insert(
                Uri.parse(DevinContentProvider.URI_ROOT_CLIENT),
                DevinContentProvider.contentValuePutClient(packageName)
            )
        } catch (e: Exception) {
            Log.e(TAG, "No Devin receiver found. Please ensure a devin presenter application is installed.")
            e.printStackTrace()
        }
    }

    companion object {

        private const val TAG = "DevinTool"

        fun create(appContext: Context): DevinTool {
            val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val packageName = appContext.packageName
            val devinTool = if (isDebuggable) {
                val logCore = LogCore(appContext, true)
                DevinTool(LoggerImpl(logCore), DevinImageLoggerImpl(logCore))
            } else DevinTool(null, null)

            if (!isDebuggable) {
                disableComponent(appContext, packageName, DevinContentProvider::class.java.name)
            } else {
                devinTool.putClient(appContext, packageName)
            }
            return devinTool
        }

        private fun disableComponent(context: Context, packageName: String, componentClassName: String) {
            val componentName = ComponentName(packageName, componentClassName)
            context.applicationContext.packageManager.setComponentEnabledSetting(
                componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}