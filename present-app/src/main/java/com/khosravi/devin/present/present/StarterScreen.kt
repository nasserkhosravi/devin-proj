package com.khosravi.devin.present.present

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import com.khosravi.devin.present.R
import com.khosravi.devin.present.client.ClientData
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
    onClientClick: (ClientData) -> Unit,
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
                title = { Text("Client selection") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.menu_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = message,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(clients, key = { _, client -> client.packageId }) { index, client ->
                    Text(
                        text = client.packageId,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClientClick(client) }
                            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                    )
                    if (index != clients.lastIndex) Divider()
                }
            }
            Text(
                text = footerText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = if (isUpdateAvailable) MaterialTheme.devinColors.updateAvailableText else LocalContentColor.current,
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.spacing.medium)
                    .padding(bottom = MaterialTheme.spacing.small)
                    .fillMaxWidth()
                    .clickable(onClick = onFooterClick)
            )
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
