package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.SessionStartLogItemData
import com.khosravi.devin.present.uikit.theme.devinColors
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun SessionStartRow(calendar: CalendarProxy, data: SessionStartLogItemData) {
    val colors = MaterialTheme.devinColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(colors.sessionStartBackground)
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colors.sessionStartDot, CircleShape)
        )
        Spacer(Modifier.width(MaterialTheme.spacing.small))
        Text(
            text = stringResource(R.string.session_started),
            color = colors.sessionStartText,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        val chipModifier = Modifier
            .padding(start = MaterialTheme.spacing.small)
            .background(colors.sessionStartPill, MaterialTheme.shapes.medium)
            .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.xs)
        val versionName = data.appVersionName
        if (!versionName.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.session_version, versionName),
                color = colors.sessionStartPillText,
                style = MaterialTheme.typography.labelMedium,
                modifier = chipModifier
            )
        }
        Text(
            text = calendar.initIfNeed(data.datePresent).getFormatted(),
            color = colors.sessionStartPillText,
            style = MaterialTheme.typography.labelMedium,
            modifier = chipModifier
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = calendar.initIfNeed(data.timePresent).getFormatted(),
            color = colors.sessionStartSecondaryText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = MaterialTheme.spacing.small)
        )
    }
}
