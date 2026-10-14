package com.khosravi.devin.present.crash

import android.app.Application
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.analytics.Analytics
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

object CrashReporting {

    @Volatile
    private var isActivated = false

    fun init(application: Application) {
        val apiKey = BuildConfig.APPMETRICA_API_KEY
        if (apiKey.isBlank()) return

        val config = AppMetricaConfig.newConfigBuilder(apiKey)
            .withCrashReporting(true)
            .withNativeCrashReporting(false)
            .withSessionsAutoTrackingEnabled(false)
            .withAppOpenTrackingEnabled(false)
            .withLocationTracking(false)
            .build()
        AppMetrica.activate(application, config)
        Analytics.isEnabled = true
        isActivated = true
    }

    /** Reports a caught, non-fatal error. No-op when AppMetrica isn't activated (e.g. debug builds). */
    fun reportError(message: String, error: Throwable) {
        if (!isActivated) return
        AppMetrica.reportError(message, error)
    }
}
