package com.khosravi.devin.present.notification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.client.getLogNotificationConfig
import com.khosravi.devin.present.data.ClientLoadedState

class LogNotificationLaunchCoordinator(
    private val activity: ComponentActivity,
    private val onPermissionHandled: (ClientLoadedState) -> Unit,
) {
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionHandled = true
        pendingPermissionLoadState?.let(onPermissionHandled)
        pendingPermissionLoadState = null
    }

    private var targetClientId: String? = null
    private var targetTag: String? = null
    private var pendingPermissionLoadState: ClientLoadedState? = null
    private var permissionHandled = false

    fun readTarget(intent: Intent) {
        targetClientId = intent.getStringExtra(EXTRA_TARGET_CLIENT_ID)
        targetTag = intent.getStringExtra(EXTRA_TARGET_TAG)
    }

    fun requestPermissionIfNeeded(loadState: ClientLoadedState): Boolean {
        if (!permissionHandled &&
            loadState.clients().any { it.getLogNotificationConfig().isEnabled } &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPermissionLoadState = loadState
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return true
        }
        return false
    }

    fun takeTarget(loadState: ClientLoadedState): Target? {
        val requestedClientId = targetClientId ?: return null
        targetClientId = null
        val notificationTag = targetTag
        targetTag = null
        val client = when (loadState) {
            is ClientLoadedState.Single -> loadState.client.takeIf { it.id == requestedClientId }
            is ClientLoadedState.Multi -> loadState.clients.firstOrNull { it.id == requestedClientId }
            is ClientLoadedState.Zero -> null
        } ?: return null
        return Target(client, notificationTag)
    }

    private fun ClientLoadedState.clients(): List<ClientData> = when (this) {
        is ClientLoadedState.Single -> listOf(client)
        is ClientLoadedState.Multi -> clients
        is ClientLoadedState.Zero -> emptyList()
    }

    data class Target(
        val client: ClientData,
        val tag: String?,
    )

    companion object {
        const val EXTRA_TARGET_CLIENT_ID = "targetClientId"
        const val EXTRA_TARGET_TAG = "targetTag"
    }
}
