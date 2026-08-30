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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.http.HttpLogOperationStatus
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun HttpLogRow(calendar: CalendarProxy, data: HttpLogItemData, onClick: () -> Unit) {
    val statusColorRes: Int
    val statusText: String
    when (val operationStatus = data.data.operationStatus) {
        is HttpLogOperationStatus.Respond -> {
            val statusCode = operationStatus.status
            statusText = statusCode.toString()
            statusColorRes = if (statusCode in 400..600) R.color.status_error else R.color.text_primary
        }

        HttpLogOperationStatus.Requested -> {
            statusText = "Requested"
            statusColorRes = R.color.text_primary
        }

        HttpLogOperationStatus.NetworkFailed -> {
            statusText = "!!!"
            statusColorRes = R.color.status_error
        }

        HttpLogOperationStatus.Unsupported -> {
            statusText = ""
            statusColorRes = R.color.text_primary
        }
    }
    val statusColor = colorResource(statusColorRes)

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
                Text(text = data.getL1SummeryText(), color = statusColor)
                // Matches HttpLogItemView.createBinding(): domain text is deliberately fixed gray, overriding text_secondary.
                Text(text = data.getFullDomainText(), color = Color.Gray)
                Text(text = data.getTimeText(calendar), color = colorResource(R.color.text_primary))
            }
        }
    }
}
