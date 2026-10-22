package com.khosravi.devin.present.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records toasts. Text toasts are drawn by System UI on API 30+, so they are not in the app's windows;
 * they are announced as `TYPE_NOTIFICATION_STATE_CHANGED` accessibility events for the app's package.
 */
object Toasts {

    private val shown = CopyOnWriteArrayList<String>()
    @Volatile
    private var installed = false

    fun start() {
        shown.clear()
        if (installed) return
        installed = true
        Ui.automation.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                event.packageName?.toString() == Ui.PACKAGE
            ) {
                // System UI reports [message, app label]; keep each part.
                event.text.forEach { shown += it.toString() }
                android.util.Log.d("E2eToasts", "toast event ${event.className}: ${event.text}")
            }
        }
    }

    fun texts(): List<String> = shown.toList()

    fun clear() = shown.clear()

    fun waitFor(text: String, timeoutMs: Long = Waits.DEFAULT_TIMEOUT_MS) {
        Waits.require("toast \"$text\" (seen: ${texts()})", timeoutMs) { shown.contains(text) }
    }

    fun assertNone(text: String, durationMs: Long = 1_500) {
        Waits.holds("no toast \"$text\"", durationMs) { !shown.contains(text) }
    }
}

/**
 * Stubs/records activity starts the way Espresso-Intents does, with a plain [Instrumentation.ActivityMonitor]:
 * a matching stub returns its [Instrumentation.ActivityResult] instead of starting the activity (and that result
 * is delivered to `startActivityForResult` / activity-result launchers). Every start is recorded, stubbed or not.
 */
object IntentStubs {

    private class Stub(val matcher: (Intent) -> Boolean, val result: Instrumentation.ActivityResult)

    private val stubs = CopyOnWriteArrayList<Stub>()
    private val started = CopyOnWriteArrayList<Intent>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            started += Intent(intent)
            return stubs.firstOrNull { it.matcher(intent) }?.result
        }
    }

    fun install() {
        stubs.clear()
        started.clear()
        instrumentation.addMonitor(monitor)
    }

    fun uninstall() {
        instrumentation.removeMonitor(monitor)
        stubs.clear()
        started.clear()
    }

    fun stub(matcher: (Intent) -> Boolean, resultCode: Int = Activity.RESULT_CANCELED, data: Intent? = null) {
        stubs += Stub(matcher, Instrumentation.ActivityResult(resultCode, data))
    }

    /** Stubs every system chooser (share sheet / save picker wrappers) and browser `ACTION_VIEW`. */
    fun stubSystemUi() {
        stub({ it.action == Intent.ACTION_CHOOSER })
        stub({ it.action == Intent.ACTION_VIEW })
    }

    fun started(): List<Intent> = started.toList()

    fun waitForStarted(description: String, timeoutMs: Long = Waits.DEFAULT_TIMEOUT_MS, matcher: (Intent) -> Boolean): Intent {
        var found: Intent? = null
        Waits.require("$description (started: ${started().map { it.describe() }})", timeoutMs) {
            started.firstOrNull(matcher).also { found = it } != null
        }
        return found!!
    }

    fun Intent.chooserTarget(): Intent? =
        @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_INTENT)

    fun Intent.describe(): String = "Intent(action=$action, type=$type, data=$data, component=${component?.shortClassName}, " +
        "target=${chooserTarget()?.let { "${it.action}/${it.type}" }})"
}

object Clipboard {
    fun text(): String? {
        var result: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                ?: return@runOnMainSync
            val manager = activity.getSystemService(ClipboardManager::class.java)
            result = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString()
        }
        return result
    }
}
