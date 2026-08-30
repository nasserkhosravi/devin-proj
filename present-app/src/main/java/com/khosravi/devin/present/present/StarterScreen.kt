package com.khosravi.devin.present.present

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.khosravi.devin.present.R
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.uikit.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarterScreen(
    message: String,
    clients: List<ClientData>,
    onClientClick: (ClientData) -> Unit,
    onRefresh: () -> Unit,
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
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = message,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            )
            LazyColumn {
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
        }
    }
}
