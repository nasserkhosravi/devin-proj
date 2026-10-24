package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.SessionStartLogItemData
import com.khosravi.devin.present.uikit.theme.spacing

/**
 * A compact, lightly tinted band marking where an app session starts. It uses the same columns as [TextLogRow]:
 * a start icon centred under the level icons, then "Session started", the version and the date/time where log
 * messages start. Text stays in neutral colours; the tint alone sets the band apart.
 */
@Composable
fun SessionStartRow(calendar: CalendarProxy, data: SessionStartLogItemData) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val metaStyle = MaterialTheme.typography.labelSmall
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.primary.copy(alpha = BACKGROUND_ALPHA)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent edge; with the tint it marks the band without a heavy fill.
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.outline.copy(alpha = EDGE_ALPHA))
        )
        Row(
            // Same start padding as TextLogRow (minus the edge), so the icon sits under the level icons' centre.
            modifier = Modifier.padding(start = spacing.small - 3.dp, end = spacing.medium, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(LOG_ROW_ICON_SIZE), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = stringResource(R.string.session_started),
                color = colors.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = spacing.large)
            )
            val versionName = data.appVersionName
            if (!versionName.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.session_version, versionName),
                    color = colors.onSurfaceVariant,
                    style = metaStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = spacing.small)
                        .widthIn(max = 120.dp)
                        .background(colors.surfaceVariant, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            Box(modifier = Modifier.weight(1f))
            Text(
                text = calendar.initIfNeed(data.datePresent).getFormatted(),
                color = colors.onSurfaceVariant,
                style = metaStyle,
                maxLines = 1,
                modifier = Modifier.padding(start = spacing.small)
            )
            Text(
                text = "·",
                color = colors.onSurfaceVariant,
                style = metaStyle,
                modifier = Modifier.padding(horizontal = spacing.xs)
            )
            Text(
                text = calendar.initIfNeed(data.timePresent).getFormatted(),
                color = colors.onSurfaceVariant,
                style = metaStyle,
                maxLines = 1,
            )
        }
    }
}

private const val BACKGROUND_ALPHA = 0.08f
private const val EDGE_ALPHA = 0.5f
