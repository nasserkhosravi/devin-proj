package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.khosravi.devin.present.R
import com.khosravi.devin.present.uikit.text.chunkForDisplay
import com.khosravi.devin.present.uikit.theme.DevinTheme
import com.khosravi.devin.present.uikit.theme.spacing

/** Stateless log detail screen; [timeText] is pre-formatted by the host and [onCopy] copies the full detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogItemDetailScreen(
    tag: String,
    message: String,
    timeText: String,
    meta: String?,
    onCopy: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_detail_title)) },
                navigationIcon = {
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                            // Icons.AutoMirrored isn't available in this Compose version, so mirror manually.
                            modifier = Modifier.graphicsLayer { if (isRtl) scaleX = -1f }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCopy) {
                        Icon(
                            painterResource(R.drawable.baseline_content_copy_24),
                            contentDescription = stringResource(R.string.menu_log_detail_copy)
                        )
                    }
                }
            )
        }
    ) { padding ->
        SelectionContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(MaterialTheme.spacing.medium)
            ) {
                InlineField(label = stringResource(R.string.field_tag), value = tag)
                InlineField(
                    label = stringResource(R.string.field_time),
                    value = timeText,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.small)
                )
                BlockField(label = stringResource(R.string.field_message), value = message)
                if (meta != null) {
                    BlockField(label = stringResource(R.string.field_meta), value = meta)
                }
            }
        }
    }
}

@Composable
private fun InlineField(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        DisableSelection {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(LABEL_WIDTH_FRACTION)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium)
        )
    }
}

@Composable
private fun BlockField(label: String, value: String) {
    DisableSelection {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = MaterialTheme.spacing.medium)
        )
    }
    val chunks = remember(value) { chunkForDisplay(value) }
    Column(modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small)) {
        chunks.forEachIndexed { index, chunk ->
            // A boundary '\n' would add a blank line since each chunk already starts a new row; keep the last chunk's.
            val display = if (index < chunks.lastIndex) chunk.removeSuffix("\n") else chunk
            Text(text = display, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Matches the old XML guideline at 28% of the content width. */
private const val LABEL_WIDTH_FRACTION = 0.28f

@Preview
@Composable
private fun LogItemDetailScreenPreview() {
    DevinTheme {
        LogItemDetailScreen(
            tag = "MyTag",
            message = "A quite long log message that describes what happened in the app.",
            timeText = "11:52:49.123",
            meta = "{\"userId\":42}",
            onCopy = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun LogItemDetailScreenNoMetaPreview() {
    DevinTheme {
        LogItemDetailScreen(
            tag = "MyTag",
            message = "No meta on this one.",
            timeText = "11:52:49.123",
            meta = null,
            onCopy = {},
            onBack = {},
        )
    }
}
