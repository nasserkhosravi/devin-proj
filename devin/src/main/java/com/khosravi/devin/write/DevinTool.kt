package com.khosravi.devin.write

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import io.nasser.devin.api.DevinLogger

class DevinTool private constructor(
    val logger: DevinLogger?
) {

    private fun putClient(appContext: Context, packageName: String) {
        appContext.contentResolver.insert(
            Uri.parse(DevinContentProvider.URI_ROOT_CLIENT),
            DevinContentProvider.contentValuePutClient(packageName)
        )
    }

    companion object {

        fun create(appContext: Context): DevinTool {
            val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val logger: DevinLogger? = if (isDebuggable) LoggerImpl(appContext, true) else null
            val packageName = appContext.packageName
            val devinTool = DevinTool(logger)
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