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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.khosravi.devin.present.R
import com.khosravi.devin.present.present.http.JsonConfigColor
import com.khosravi.devin.present.present.stacktrace.StackTrace
import com.khosravi.devin.present.present.stacktrace.StackTraceBlock
import com.khosravi.devin.present.uikit.text.CodeBlock
import com.khosravi.devin.present.uikit.text.chunkForDisplay
import com.khosravi.devin.present.uikit.text.codeTextStyle
import com.khosravi.devin.present.uikit.theme.DevinTheme
import com.khosravi.devin.present.uikit.theme.spacing

/**
 * Stateless log detail screen; [timeText] is pre-formatted by the host and [onCopy] copies the full detail.
 * A parsed [stackTrace] gets its own block (with [appPackage]'s frames highlighted); [meta] is then shown without it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogItemDetailScreen(
    tag: String,
    message: String,
    timeText: String,
    meta: String?,
    stackTrace: StackTrace?,
    appPackage: String?,
    onCopyStackTrace: () -> Unit,
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
                if (stackTrace != null) {
                    StackTraceBlock(stackTrace, appPackage, onCopyStackTrace)
                }
                if (meta != null) {
                    MetaField(meta)
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
    FieldLabel(label)
    val chunks = remember(value) { chunkForDisplay(value) }
    Column(modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small)) {
        chunks.forEachIndexed { index, chunk ->
            // A boundary '\n' would add a blank line since each chunk already starts a new row; keep the last chunk's.
            val display = if (index < chunks.lastIndex) chunk.removeSuffix("\n") else chunk
            Text(text = display, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Meta as coloured, pretty-printed JSON in a code block; falls back to [BlockField] when it isn't JSON. */
@Composable
private fun MetaField(meta: String) {
    val context = LocalContext.current
    val lines = remember(meta) { formatMetaJson(meta, JsonConfigColor.create(context)) }
    if (lines == null) {
        BlockField(label = stringResource(R.string.field_meta), value = meta)
        return
    }
    FieldLabel(stringResource(R.string.field_meta))
    CodeBlock(modifier = Modifier.padding(top = MaterialTheme.spacing.small)) {
        lines.forEach { Text(text = it, style = codeTextStyle) }
    }
}

@Composable
private fun FieldLabel(label: String) {
    DisableSelection {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = MaterialTheme.spacing.medium)
        )
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
            meta = "{\"log_level\":4,\"fields\":{\"userId\":42,\"premium\":true}}",
            stackTrace = null,
            appPackage = null,
            onCopyStackTrace = {},
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
            stackTrace = null,
            appPackage = null,
            onCopyStackTrace = {},
            onCopy = {},
            onBack = {},
        )
    }
}
