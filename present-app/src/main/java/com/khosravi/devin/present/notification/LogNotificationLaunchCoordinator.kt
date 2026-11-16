package com.khosravi.devin.present.notification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.analytics.Analytics
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.client.getLogNotificationConfig
import com.khosravi.devin.present.data.ClientLoadedState

class LogNotificationLaunchCoordinator(
    private val activity: ComponentActivity,
    private val onPermissionHandled: (ClientLoadedState) -> Unit,
) {
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Analytics.notificationPermissionResult(isGranted)
        permissionHandled = true
        pendingPermissionLoadState?.let(onPermissionHandled)
        pendingPermissionLoadState = null
    }

    private var targetClientId: String? = null
    private var targetTag: String? = null
    private var isDeepLink = false
    private var pendingPermissionLoadState: ClientLoadedState? = null
    private var permissionHandled = false

    fun readTarget(intent: Intent) {
        val deepLink = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW }?.let(::parseDeepLink)
        isDeepLink = deepLink != null
        targetClientId = deepLink?.first ?: intent.getStringExtra(EXTRA_TARGET_CLIENT_ID)
        targetTag = deepLink?.second ?: intent.getStringExtra(EXTRA_TARGET_TAG)
    }

    /** `devin://client/<packageId>[?tag=<tag>]` -> (packageId, tag), or null when it is not that shape. */
    private fun parseDeepLink(uri: Uri): Pair<String, String?>? {
        if (uri.scheme != DEEP_LINK_SCHEME || uri.host != DEEP_LINK_HOST) return null
        val clientId = uri.pathSegments.singleOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return clientId to uri.getQueryParameter(DEEP_LINK_PARAM_TAG)?.takeIf { it.isNotBlank() }
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
        val source = if (isDeepLink) Analytics.ClientOpenSource.DEEPLINK else Analytics.ClientOpenSource.NOTIFICATION
        val client = when (loadState) {
            is ClientLoadedState.Single -> loadState.client.takeIf { it.id == requestedClientId }
            is ClientLoadedState.Multi -> loadState.clients.firstOrNull { it.id == requestedClientId }
            is ClientLoadedState.Zero -> null
        } ?: return null
        return Target(client, notificationTag, source)
    }

    private fun ClientLoadedState.clients(): List<ClientData> = when (this) {
        is ClientLoadedState.Single -> listOf(client)
        is ClientLoadedState.Multi -> clients
        is ClientLoadedState.Zero -> emptyList()
    }

    data class Target(
        val client: ClientData,
        val tag: String?,
        val source: Analytics.ClientOpenSource,
    )

    companion object {
        const val EXTRA_TARGET_CLIENT_ID = "targetClientId"
        const val EXTRA_TARGET_TAG = "targetTag"
        const val DEEP_LINK_SCHEME = "devin"
        const val DEEP_LINK_HOST = "client"
        const val DEEP_LINK_PARAM_TAG = "tag"
    }
}
