package com.khosravi.devin.present.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.present.StarterActivity
import com.khosravi.devin.read.DevinUriHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LatestLogNotificationObserver(
    context: Context,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changeSignals = Channel<Unit>(Channel.CONFLATED)
    private val largeIcon by lazy {
        BitmapFactory.decodeResource(appContext.resources, R.drawable.ic_logo)
    }

    init {
        createNotificationChannel()
        scope.launch {
            for (ignored in changeSignals) {
                delay(REFRESH_INTERVAL_MILLIS)
                while (changeSignals.tryReceive().isSuccess) {
                    // Discard intermediate changes; the database query below reads the newest log.
                }
                publishLatestLog()
            }
        }
    }

    fun register() {
        appContext.contentResolver.registerContentObserver(
            DevinUriHelper.getLogListUri(),
            true,
            this,
        )
    }

    override fun onChange(selfChange: Boolean) {
        changeSignals.trySend(Unit)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        changeSignals.trySend(Unit)
    }

    @SuppressLint("MissingPermission")
    private fun publishLatestLog() {
        if (!canPostNotifications()) return
        val latestLog = ContentProviderLogsDao.getLatestLog(appContext) ?: return
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(latestLog))
        } catch (exception: SecurityException) {
            Log.w(TAG, "Notification permission was revoked before the latest log could be published", exception)
        }
    }

    private fun buildNotification(log: LogData) = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_logo)
        .setContentTitle(log.tag)
        .setContentText(log.value.toNotificationMessage())
        .setContentIntent(createContentIntent(log.packageId))
        .setAutoCancel(true)
        .build()

    private fun createContentIntent(clientId: String): PendingIntent {
        val intent = Intent(appContext, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_TARGET_CLIENT_ID, clientId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            CONTENT_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return notificationManager.areNotificationsEnabled()
    }

    private fun createNotificationChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.log_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun String.toNotificationMessage(): String {
        val characterCount = codePointCount(0, length)
        if (characterCount <= MAX_MESSAGE_CHARACTERS) return this
        val endIndex = offsetByCodePoints(0, MAX_MESSAGE_CHARACTERS - 1)
        return substring(0, endIndex) + ELLIPSIS
    }

    companion object {
        private const val TAG = "LatestLogNotification"
        private const val CHANNEL_ID = "devin_log_events"
        private const val NOTIFICATION_ID = 1001
        private const val CONTENT_INTENT_REQUEST_CODE = 1001
        private const val MAX_MESSAGE_CHARACTERS = 160
        private const val REFRESH_INTERVAL_MILLIS = 1_000L
        private const val ELLIPSIS = "…"
    }
}
