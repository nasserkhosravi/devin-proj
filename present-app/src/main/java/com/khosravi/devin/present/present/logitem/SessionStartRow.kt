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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.SessionStartLogItemData

@Composable
fun SessionStartRow(calendar: CalendarProxy, data: SessionStartLogItemData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(colorResource(R.color.session_start_background))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colorResource(R.color.session_start_dot), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.session_started),
            color = colorResource(R.color.session_start_text),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        val chipModifier = Modifier
            .padding(start = 10.dp)
            .background(Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
        val versionName = data.appVersionName
        if (!versionName.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.session_version, versionName),
                color = colorResource(R.color.session_start_secondary_text),
                fontSize = 12.sp,
                modifier = chipModifier
            )
        }
        Text(
            text = calendar.initIfNeed(data.datePresent).getFormatted(),
            color = colorResource(R.color.session_start_secondary_text),
            fontSize = 12.sp,
            modifier = chipModifier
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = calendar.initIfNeed(data.timePresent).getFormatted(),
            color = colorResource(R.color.session_start_secondary_text),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
