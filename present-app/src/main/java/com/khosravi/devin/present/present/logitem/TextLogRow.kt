package com.khosravi.devin.present.present.logitem

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.uikit.theme.LogLevelColors
import com.khosravi.devin.present.uikit.theme.devinColors
import com.khosravi.devin.present.uikit.theme.spacing

private data class LogLevelStyle(
    val label: String,
    val icon: Int,
    val colors: LogLevelColors,
)

@Composable
private fun logLevelStyle(logLevel: Int): LogLevelStyle? {
    val colors = MaterialTheme.devinColors
    return when (logLevel) {
        Log.ERROR -> LogLevelStyle(stringResource(R.string.log_item_error), R.drawable.ic_info_24px, colors.logError)
        Log.WARN -> LogLevelStyle(stringResource(R.string.log_item_warn), R.drawable.ic_warning_24px, colors.logWarning)
        Log.INFO -> LogLevelStyle(stringResource(R.string.log_item_info), R.drawable.ic_info_24px, colors.logInfo)
        Log.DEBUG -> LogLevelStyle(stringResource(R.string.log_item_debug), R.drawable.ic_bug_report_24px, colors.logDebug)
        Log.VERBOSE -> LogLevelStyle(stringResource(R.string.log_item_verbose), R.drawable.ic_bug_report_24px, colors.logDebug)
        else -> null
    }
}

@Composable
fun TextLogRow(
    calendar: CalendarProxy,
    data: TextLogItemData,
    ignoreTagChip: Boolean,
    onClick: () -> Unit,
) {
    val dateText = calendar.initIfNeed(data.timePresent).getFormatted()
    val style = logLevelStyle(data.logLevel)
    val previewText = remember(data.text) { data.text.toRowPreview() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.small)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = style?.colors?.iconBackground ?: MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                style?.let {
                    Icon(
                        painter = painterResource(it.icon),
                        contentDescription = null,
                        tint = it.colors.icon,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = MaterialTheme.spacing.large)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    style?.let {
                        Text(
                            text = it.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = it.colors.badgeText,
                            modifier = Modifier
                                .background(it.colors.badgeBackground, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = MaterialTheme.spacing.xs)
                        )
                    }
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                    if (!ignoreTagChip) {
                        Text(
                            text = data.tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = MaterialTheme.spacing.small, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = TEXT_LOG_PREVIEW_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.xs)
                )
            }
        }
    }
}
