package com.khosravi.devin.write

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import io.nasser.devin.api.DevinCustomValue
import io.nasser.devin.api.DevinImageLogger
import io.nasser.devin.api.DevinLogger

class DevinTool private constructor(
    val logger: DevinLogger?,
    val imageLogger: DevinImageLogger?,
    val customValue: DevinCustomValue?,
) {

    private fun putClient(appContext: Context, packageName: String) {
        appContext.contentResolver.insert(
            Uri.parse(DevinContentProvider.URI_ROOT_CLIENT),
            DevinContentProvider.contentValuePutClient(packageName)
        )
    }

    companion object {
        //sync API to no-op version
        private const val TAG = "DevinTool"

        private var instance: DevinTool? = null

        private fun create(appContext: Context): DevinTool {
            val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val packageName = appContext.packageName
            val devinTool = if (isDebuggable) {
                val logCore = LogCore(appContext, true)
                DevinTool(LoggerImpl(logCore), DevinImageLoggerImpl(logCore), DevinCustomValueImpl(logCore.isEnable))
            } else DevinTool(null, null, null)

            if (!isDebuggable) {
                //TODO: it crashes when devin-op is release, it should be remove because DevinContentProvider does not belong
                // to its user.
//                disableComponent(appContext, packageName, DevinContentProvider::class.java.name)
                return devinTool
            } else {
                try {
                    devinTool.putClient(appContext, packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "No Devin receiver found. Please ensure a devin presenter application is installed.")
                    e.printStackTrace()
                    return DevinTool(null, null, null)
                }
            }
            return devinTool
        }

        fun get(): DevinTool? = instance

        fun getOrCreate(context: Context): DevinTool? {
            if (instance == null) {
                instance = create(context)
            }
            return instance
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