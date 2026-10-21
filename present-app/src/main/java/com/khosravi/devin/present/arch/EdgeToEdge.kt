package com.khosravi.devin.present.arch

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.core.view.WindowCompat

// Same scrims androidx.activity's enableEdgeToEdge() uses where the system can't tint the navigation bar icons itself.
private val LIGHT_NAV_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAV_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * Draws the activity edge-to-edge on every API level, like Android 15+ enforces for targetSdk 35, so the
 * Compose content handles the system-bar (and IME) insets itself.
 *
 * A stand-in for androidx.activity's `enableEdgeToEdge()`, which needs activity 1.8+. Call it from `onCreate`
 * after `super.onCreate`. Bar icon colours follow the activity's current night mode, so they are right again
 * after the in-app theme toggle recreates the activity.
 */
@Suppress("DEPRECATION") // statusBarColor/navigationBarColor: ignored on 35+, still needed below it.
fun Activity.applyEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = when {
        // The system adds its own scrim behind 3-button navigation (contrast enforced) and none behind the gesture handle.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Color.TRANSPARENT
        // Light navigation bar icons exist from API 27 (O_MR1).
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> if (isDark) DARK_NAV_SCRIM else LIGHT_NAV_SCRIM
        else -> DARK_NAV_SCRIM
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = true
    }
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !isDark
        isAppearanceLightNavigationBars = !isDark
    }
}
