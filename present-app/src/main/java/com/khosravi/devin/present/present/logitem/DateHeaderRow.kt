package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun DateHeaderRow(calendar: CalendarProxy, data: DateLogItemData) {
    val dateText = calendar.initIfNeed(data.presentDate).getFormatted()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.small),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.tertiary,
        ) {
            Text(
                text = dateText,
                color = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
