package com.khosravi.devin.write

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.khosravi.devin.api.DevinImageLogger
import com.khosravi.devin.api.core.DevinLogCore
import com.khosravi.devin.api.DevinLogger
import java.lang.Exception

class DevinTool private constructor(
    val logger: DevinLogger?,
    val imageLogger: DevinImageLogger?,
    private val logCore: DevinLogCore? = null,
) {

    private fun putClient(appContext: Context, packageName: String) {
        appContext.contentResolver.insert(
            DevinContentProvider.uriOfClient(),
            DevinContentProvider.contentValuePutClient(packageName)
        )
    }

    /**
     * Give available [DevinLogCore] instance.
     */
    fun connectPlugin(action: (logCore: DevinLogCore) -> Unit) {
        logCore?.let { action.invoke(it) }
    }

    companion object {
        //sync API to no-op version
        private const val TAG = "DevinTool"

        private var instance: DevinTool? = null


        private fun create(appContext: Context, isEnable: Boolean): DevinTool {
            val packageName = appContext.packageName
            val devinTool = if (isEnable) {
                val logCore = LogCore(appContext, true)
                DevinTool(LoggerImpl(logCore), DevinImageLoggerImpl(logCore), logCore)
            } else DevinTool(null, null, null)

            if (!isEnable) {
                disableComponent(appContext, packageName, DevinContentProvider::class.java.name)
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

        fun create(context: Context, isEnable: Boolean? = null): DevinTool {
            if (instance == null) {
                val fIsEnable: Boolean = isEnable ?: context.isDebuggable()
                instance = create(context, fIsEnable)
            }
            return instance!!
        }

        fun get(): DevinTool? = instance

        fun getOrCreate(context: Context): DevinTool? {
            if (instance == null) {
                instance = create(context)
            }
            return instance
        }

        private fun Context.isDebuggable() = ((applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)

        private fun disableComponent(context: Context, packageName: String, componentClassName: String) {
            val componentName = ComponentName(packageName, componentClassName)
            context.applicationContext.packageManager.setComponentEnabledSetting(
                componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}