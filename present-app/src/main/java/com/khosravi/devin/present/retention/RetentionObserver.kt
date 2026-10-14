package com.khosravi.devin.present.retention

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.khosravi.devin.present.crash.CrashReporting
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.room.DevinDB
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers [RetentionCleaner] off the back of log inserts. Any client write starts present-app's
 * process (it hosts `DevinContentProvider`), so this sees every insert without a background job.
 *
 * Trims are throttled to one per [THROTTLE_MILLIS]; the first insert after process start always
 * trims. Work runs on a single-thread executor so trims never overlap or block the provider.
 */
@Singleton
class RetentionObserver @Inject constructor(
    context: Context,
    private val appPref: AppPref,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val lastTrimAt = AtomicLong(0L)

    fun register() {
        appContext.contentResolver.registerContentObserver(DevinUriHelper.getLogListUri(), true, this)
    }

    /** Makes the next insert trim immediately; call after the user changes a retention setting. */
    fun resetThrottle() {
        lastTrimAt.set(0L)
    }

    override fun onChange(selfChange: Boolean) {
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        if (uri == null || DevinUriHelper.getLogChange(uri) == null) return
        val now = System.currentTimeMillis()
        val last = lastTrimAt.get()
        if (now - last < THROTTLE_MILLIS) return
        val policy = appPref.retentionPolicy
        if (!policy.isEnabled) return
        if (!lastTrimAt.compareAndSet(last, now)) return
        executor.execute { trim(policy) }
    }

    private fun trim(policy: RetentionPolicy) {
        try {
            val db = DevinDB.getInstance(appContext).openHelper.writableDatabase
            val result = RetentionCleaner(db, appContext.filesDir).trim(policy, appPref.lastVacuumAt)
            if (result.vacuumed) {
                appPref.lastVacuumAt = System.currentTimeMillis()
            }
            Log.d(TAG, "Retention trim done: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Retention trim failed", e)
            CrashReporting.reportError("Retention trim failed", e)
        }
    }

    private companion object {
        const val TAG = "RetentionObserver"
        val THROTTLE_MILLIS = TimeUnit.MINUTES.toMillis(10)
    }
}
