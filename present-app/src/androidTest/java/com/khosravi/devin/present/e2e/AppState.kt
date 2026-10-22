package com.khosravi.devin.present.e2e

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.khosravi.devin.present.PresentApplication
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.present.update.ReleaseInfo
import com.khosravi.devin.present.update.UpdateChecker
import com.khosravi.devin.write.room.DevinDB
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Puts the app back into a "fresh install" state inside the running process: no activities, empty DB,
 * empty SharedPreferences (cleared through the cached instances, so singletons such as `AppPref` see it),
 * no cached update, no notifications. The suite runs without the test orchestrator, so this replaces
 * `clearPackageData` (see README.md).
 */
object AppState {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context: Context get() = instrumentation.targetContext.applicationContext

    fun reset() {
        finishAllActivities()
        DevinDB.getInstance(context).clearAllTables()
        clearAllSharedPreferences()
        suppressUpdateCheck(context)
        setCachedUpdate(null)
        File(context.cacheDir, "temp_shared").deleteRecursively()
        File(context.cacheDir, E2E_DIR).deleteRecursively()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        (context as PresentApplication).appComponent.retentionObserver().resetThrottle()
    }

    /** Makes `UpdateChecker.checkInBackgroundIfDue()` a no-op for the next 24 hours. */
    fun suppressUpdateCheck(context: Context) {
        AppPref(context).saveUpdateCheckTime(System.currentTimeMillis())
    }

    /**
     * Seeds the cached "latest release" the way a finished GitHub check does, without network, and pushes
     * it into the process-wide [UpdateChecker] (its flow is only read from prefs at construction).
     */
    fun seedLatestRelease(version: String, url: String) {
        AppPref(context).saveUpdateCheckResult(System.currentTimeMillis(), version, url)
        val checker = updateChecker()
        val read = UpdateChecker::class.java.getDeclaredMethod("readAvailableUpdate").apply { isAccessible = true }
        setCachedUpdate(read.invoke(checker) as ReleaseInfo?)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setCachedUpdate(update: ReleaseInfo?) {
        val field = UpdateChecker::class.java.getDeclaredField("_availableUpdate").apply { isAccessible = true }
        (field.get(updateChecker()) as MutableStateFlow<ReleaseInfo?>).value = update
    }

    private fun updateChecker(): UpdateChecker = (context as PresentApplication).appComponent.updateChecker()

    private fun clearAllSharedPreferences() {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        dir.listFiles()?.filter { it.name.endsWith(".xml") }?.forEach { file ->
            context.getSharedPreferences(file.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    fun e2eDir(): File = File(context.cacheDir, E2E_DIR).apply { mkdirs() }

    fun activities(): List<Activity> {
        var result: List<Activity> = emptyList()
        instrumentation.runOnMainSync { result = activitiesOnMain() }
        return result
    }

    private fun activitiesOnMain(): List<Activity> {
        val registry = ActivityLifecycleMonitorRegistry.getInstance()
        return Stage.values().filter { it != Stage.DESTROYED }.flatMap { registry.getActivitiesInStage(it) }
    }

    fun resumedActivity(): Activity? {
        var result: Activity? = null
        instrumentation.runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return result
    }

    fun finishAllActivities() {
        repeat(3) {
            instrumentation.runOnMainSync { activitiesOnMain().forEach { if (!it.isFinishing) it.finish() } }
            if (Waits.until(5_000) { activities().isEmpty() }) return
        }
        check(activities().isEmpty()) { "Activities still alive: ${activities()}" }
    }

    private const val E2E_DIR = "e2e"
}
