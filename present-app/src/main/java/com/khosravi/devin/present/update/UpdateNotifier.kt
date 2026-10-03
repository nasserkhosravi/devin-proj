package com.khosravi.devin.present.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.R

class UpdateNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    /** @return true if the notification was posted. */
    @SuppressLint("MissingPermission")
    fun notify(update: ReleaseInfo): Boolean {
        if (!canPostNotifications()) return false
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(appContext.getString(R.string.update_notification_channel_name))
                .build()
        )
        val contentIntent = PendingIntent.getActivity(
            appContext,
            CONTENT_INTENT_REQUEST_CODE,
            openReleaseIntent(update),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(appContext.updateTitle(update))
            .setContentText(appContext.updateMessage(update))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        return try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Notification permission was revoked before the update notification could be posted", exception)
            false
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return notificationManager.areNotificationsEnabled()
    }

    companion object {
        private const val TAG = "UpdateNotifier"
        private const val CHANNEL_ID = "app_update"
        private const val NOTIFICATION_ID = 2001
        private const val CONTENT_INTENT_REQUEST_CODE = 2001

        fun openReleaseIntent(update: ReleaseInfo): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
