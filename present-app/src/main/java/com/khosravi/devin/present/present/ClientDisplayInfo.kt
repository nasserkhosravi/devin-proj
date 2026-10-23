package com.khosravi.devin.present.present

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.ContentProviderLogsDao

/**
 * What the client list shows for a client besides its package id. [label] and [icon] are null when
 * the client app isn't installed or isn't visible to the presenter; [logCount] is null if the count failed.
 */
data class ClientDisplayInfo(
    val label: String?,
    val icon: ImageBitmap?,
    val logCount: Int?,
)

/** Reads the app label, icon and log count of [client]. Does IO, so call it off the main thread. */
fun loadClientDisplayInfo(context: Context, client: ClientData): ClientDisplayInfo {
    val pm = context.packageManager
    val appInfo = try {
        pm.getApplicationInfo(client.packageId, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
    return ClientDisplayInfo(
        label = appInfo?.let { pm.getApplicationLabel(it).toString() },
        icon = appInfo?.let { pm.getApplicationIcon(it).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap() },
        logCount = ContentProviderLogsDao.countLogs(context, client.packageId),
    )
}

private const val ICON_SIZE_PX = 128
