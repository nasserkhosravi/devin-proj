package com.khosravi.devin.present.present

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.khosravi.devin.present.R
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.client.getLogNotificationConfig
import com.khosravi.devin.present.uikit.theme.devinColors
import com.khosravi.devin.present.uikit.theme.spacing
import com.khosravi.devin.present.update.ReleaseInfo
import com.khosravi.devin.present.update.updateMessage
import com.khosravi.devin.present.update.updateTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarterScreen(
    message: String,
    clients: List<ClientData>,
    clientInfo: Map<String, ClientDisplayInfo>,
    onClientClick: (ClientData) -> Unit,
    areNotificationsEnabled: Boolean,
    onNotificationsDisabledClick: () -> Unit,
    onRefresh: () -> Unit,
    footerText: String,
    isUpdateAvailable: Boolean,
    onFooterClick: () -> Unit,
    forceUpdate: ReleaseInfo?,
    onForceUpdateDownload: (ReleaseInfo) -> Unit,
    onForceUpdateDismiss: () -> Unit,
    optionalUpdate: ReleaseInfo?,
    onOptionalUpdateDownload: () -> Unit,
    onOptionalUpdateClose: () -> Unit,
    onOptionalUpdateLater: () -> Unit = onOptionalUpdateClose,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.client_selection_title)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.menu_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val wantsNotifications = remember(clients) { clients.any { it.getLogNotificationConfig().isEnabled } }
            // A client opted into log notifications, but the system won't show any for the presenter.
            if (wantsNotifications && !areNotificationsEnabled) {
                NotificationsDisabledBanner(onEnableClick = onNotificationsDisabledClick)
            }
            Text(
                text = message,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                items(clients, key = { it.packageId }) { client ->
                    ClientCard(client = client, info = clientInfo[client.packageId], onClick = { onClientClick(client) })
                }
            }
            // A button, so it reads as tappable: it opens the release page.
            TextButton(
                onClick = onFooterClick,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = MaterialTheme.spacing.small)
            ) {
                Text(
                    text = footerText,
                    color = if (isUpdateAvailable) MaterialTheme.devinColors.updateAvailableText else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (forceUpdate != null) {
        // Not dismissable by an outside tap; back calls onForceUpdateDismiss.
        UpdateDialog(
            update = forceUpdate,
            onDownload = { onForceUpdateDownload(forceUpdate) },
            onDismissRequest = onForceUpdateDismiss,
            onLater = null,
            properties = DialogProperties(dismissOnClickOutside = false),
        )
    }
    // A pending force update takes over; the optional prompt waits behind it (old code stacked the force dialog on top).
    if (optionalUpdate != null && forceUpdate == null) {
        UpdateDialog(
            update = optionalUpdate,
            onDownload = onOptionalUpdateDownload,
            onDismissRequest = onOptionalUpdateClose,
            onLater = onOptionalUpdateLater,
        )
    }
}

@Composable
private fun NotificationsDisabledBanner(onEnableClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.small, top = MaterialTheme.spacing.small, bottom = MaterialTheme.spacing.small)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notifications_disabled_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.notifications_disabled_message),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onEnableClick) {
                Text(stringResource(R.string.notifications_disabled_action), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientCard(client: ClientData, info: ClientDisplayInfo?, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium, end = MaterialTheme.spacing.small, top = 12.dp, bottom = 12.dp)
        ) {
            ClientIcon(client = client, info = info)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MaterialTheme.spacing.medium)
            ) {
                // The package id is the title only when the app's label can't be read (uninstalled or not visible).
                Text(
                    text = info?.label ?: client.packageId,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info?.label != null) {
                    Text(
                        text = client.packageId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            info?.logCount?.let { count ->
                Text(
                    text = pluralStringResource(R.plurals.client_log_count, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The app icon, or the first letter of the package id while it loads or when it can't be read. */
@Composable
private fun ClientIcon(client: ClientData, info: ClientDisplayInfo?) {
    val icon = info?.icon
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(CLIENT_ICON_SIZE))
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CLIENT_ICON_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Text(
                text = (info?.label ?: client.packageId.substringAfterLast('.')).take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private val CLIENT_ICON_SIZE = 40.dp

@Composable
private fun UpdateDialog(
    update: ReleaseInfo,
    onDownload: () -> Unit,
    onDismissRequest: () -> Unit,
    onLater: (() -> Unit)?,
    properties: DialogProperties = DialogProperties(),
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(context.updateTitle(update)) },
        text = { Text(context.updateMessage(update)) },
        confirmButton = {
            TextButton(onClick = onDownload) { Text(stringResource(R.string.update_action_download)) }
        },
        dismissButton = onLater?.let { later ->
            { TextButton(onClick = later) { Text(stringResource(R.string.update_action_later)) } }
        },
        properties = properties,
    )
}
