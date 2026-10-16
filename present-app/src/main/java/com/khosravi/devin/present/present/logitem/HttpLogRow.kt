package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.data.http.HttpLogOperationStatus
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.uikit.theme.devinColors
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun HttpLogRow(calendar: CalendarProxy, data: HttpLogItemData, onClick: () -> Unit) {
    val statusError = MaterialTheme.devinColors.statusError
    val onSurface = MaterialTheme.colorScheme.onSurface
    val statusColor: Color
    val statusText: String
    when (val operationStatus = data.data.operationStatus) {
        is HttpLogOperationStatus.Respond -> {
            val statusCode = operationStatus.status
            statusText = statusCode.toString()
            statusColor = if (statusCode in 400..600) statusError else onSurface
        }

        HttpLogOperationStatus.Requested -> {
            statusText = "Requested"
            statusColor = onSurface
        }

        HttpLogOperationStatus.NetworkFailed -> {
            statusText = "!!!"
            statusColor = statusError
        }

        HttpLogOperationStatus.Unsupported -> {
            statusText = ""
            statusColor = onSurface
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.xs)
        ) {
            Text(text = statusText, color = statusColor, modifier = Modifier.width(56.dp))
            Column {
                Text(
                    text = data.getL1SummeryText().toRowPreview(),
                    color = statusColor,
                    maxLines = URL_PREVIEW_MAX_LINES,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = data.getFullDomainText().toRowPreview(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = URL_PREVIEW_MAX_LINES,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = data.getTimeText(calendar), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
