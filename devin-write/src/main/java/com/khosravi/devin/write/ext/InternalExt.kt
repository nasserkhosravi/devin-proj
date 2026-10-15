package com.khosravi.devin.write.ext

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.DisplayMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object InternalExt {

    fun getSessionStartPayload(context: Context, packageInfo: PackageInfo): String {
        val (appVersionName, appVersionCode) = getAppVersionNameAndCode(packageInfo)

        val deviceManufacturer = Build.MANUFACTURER
        val deviceModel = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val screenResolution = "${metrics.widthPixels}x${metrics.heightPixels}"
        val screenDensity = metrics.densityDpi

        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        return """Timestamp: $timestamp
        App Version: $appVersionName ($appVersionCode)
        Android: $androidVersion (SDK $sdkInt)
        Device: $deviceManufacturer $deviceModel
        Screen: $screenResolution @${screenDensity}dpi"""
    }

    fun getAppVersionNameAndCode(packageInfo: PackageInfo): Pair<String?, Long> {
        val appVersionName = packageInfo.versionName
        val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return Pair(appVersionName, appVersionCode)
    }
}