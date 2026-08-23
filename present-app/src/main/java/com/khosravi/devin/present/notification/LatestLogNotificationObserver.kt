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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.R
import com.khosravi.devin.present.client.LogNotificationConfig
import com.khosravi.devin.present.client.getLogNotificationConfig
import com.khosravi.devin.present.data.ClientContentProvider
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.present.StarterActivity
import com.khosravi.devin.read.DevinUriHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class LatestLogNotificationObserver(
    context: Context,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clientChangeSignals = Channel<Unit>(Channel.CONFLATED)
    private val observedLogChanges = Channel<DevinUriHelper.LogChange>(Channel.CONFLATED)
    private val eligibleLogChanges = Channel<GroupedChange>(Channel.UNLIMITED)
    private val requestedConfigGeneration = AtomicInteger(INITIAL_CONFIG_GENERATION)
    private val pendingPublishJobs = mutableMapOf<String, Job>()
    @Volatile
    private var loadedConfigGeneration = 0
    @Volatile
    private var clientConfigs: Map<String, LogNotificationConfig> = emptyMap()
    private val clientObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            requestClientConfigReload()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            requestClientConfigReload()
        }
    }

    init {
        createNotificationChannel()
        scope.launch {
            for (ignored in clientChangeSignals) {
                while (loadedConfigGeneration < requestedConfigGeneration.get()) {
                    val generation = requestedConfigGeneration.get()
                    reloadClientConfigs()
                    loadedConfigGeneration = generation
                }
            }
        }
        scope.launch {
            for (change in observedLogChanges) {
                while (loadedConfigGeneration < requestedConfigGeneration.get()) {
                    delay(CONFIG_RELOAD_WAIT_MILLIS)
                }
                val group = clientConfigs[change.clientId]?.groupFor(change.tag)
                if (group != null) {
                    eligibleLogChanges.trySend(GroupedChange(change, group.name))
                }
            }
        }
        scope.launch {
            for (grouped in eligibleLogChanges) {
                val key = groupKey(grouped.logChange.clientId, grouped.groupName)
                pendingPublishJobs[key]?.cancel()
                pendingPublishJobs[key] = scope.launch {
                    delay(REFRESH_INTERVAL_MILLIS)
                    publishLatestLog(grouped.logChange, grouped.groupName)
                }
            }
        }
    }

    fun register() {
        appContext.contentResolver.registerContentObserver(
            DevinUriHelper.getClientListUri(),
            false,
            clientObserver,
        )
        clientChangeSignals.trySend(Unit)
        appContext.contentResolver.registerContentObserver(
            DevinUriHelper.getLogListUri(),
            true,
            this,
        )
    }

    override fun onChange(selfChange: Boolean) {
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        val change = uri?.let(DevinUriHelper::getLogChange) ?: return
        if (loadedConfigGeneration < requestedConfigGeneration.get()) {
            observedLogChanges.trySend(change)
            return
        }
        val group = clientConfigs[change.clientId]?.groupFor(change.tag) ?: return
        eligibleLogChanges.trySend(GroupedChange(change, group.name))
    }

    private fun requestClientConfigReload() {
        requestedConfigGeneration.incrementAndGet()
        clientChangeSignals.trySend(Unit)
    }

    private fun reloadClientConfigs() {
        clientConfigs = try {
            ClientContentProvider.getClientList(appContext)
                .associate { it.id to it.getLogNotificationConfig() }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to reload log-notification configuration", exception)
            emptyMap()
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishLatestLog(change: DevinUriHelper.LogChange, groupName: String) {
        if (!canPostNotifications()) return
        val group = clientConfigs[change.clientId]?.groupFor(change.tag) ?: return
        if (group.name != groupName) return
        val latestLog = ContentProviderLogsDao.getLog(appContext, change.id)
            ?.takeIf { it.packageId == change.clientId && it.tag == change.tag }
            ?: return
        try {
            notificationManager.notify(
                notificationId(change.clientId, groupName),
                buildNotification(latestLog, groupName),
            )
        } catch (exception: SecurityException) {
            Log.w(TAG, "Notification permission was revoked before the latest log could be published", exception)
        }
    }

    private fun buildNotification(log: LogData, groupName: String) = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_logo)
        .setContentTitle(groupName)
        .setContentText(log.value.toNotificationMessage())
        .setContentIntent(createContentIntent(log.packageId, log.tag))
        .setAutoCancel(true)
        .build()

    private fun createContentIntent(clientId: String, tag: String): PendingIntent {
        val intent = Intent(appContext, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_TARGET_CLIENT_ID, clientId)
            putExtra(StarterActivity.EXTRA_TARGET_TAG, tag)
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

    private fun groupKey(clientId: String, groupName: String) = "$clientId|$groupName"

    private fun notificationId(clientId: String, groupName: String) = groupKey(clientId, groupName).hashCode()

    private data class GroupedChange(
        val logChange: DevinUriHelper.LogChange,
        val groupName: String,
    )

    companion object {
        private const val TAG = "LatestLogNotification"
        private const val CHANNEL_ID = "devin_log_events"
        private const val CONTENT_INTENT_REQUEST_CODE = 1001
        private const val MAX_MESSAGE_CHARACTERS = 160
        private const val REFRESH_INTERVAL_MILLIS = 1_000L
        private const val CONFIG_RELOAD_WAIT_MILLIS = 10L
        private const val INITIAL_CONFIG_GENERATION = 1
        private const val ELLIPSIS = "…"
    }
}
