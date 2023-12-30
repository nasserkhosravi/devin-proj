package com.khosravi.devin.write

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class DevinTool private constructor(
    val logger: DevinLogger?
) {

    companion object {

        fun create(appContext: Context): DevinTool {
            val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            val logger: DevinLogger? = if (isDebuggable) LoggerImpl(appContext, true) else null

            if (!isDebuggable) {
                disableComponent(appContext, appContext.packageName!!, DevinContentProvider::class.java.name)
            }
            return DevinTool(logger)
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