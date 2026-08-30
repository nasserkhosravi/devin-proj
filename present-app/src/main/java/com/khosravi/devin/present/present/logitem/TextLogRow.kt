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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.uikit.theme.spacing

private data class LogLevelStyle(
    val label: String,
    val icon: Int,
    val iconColor: Int,
    val iconBgColor: Int,
    val badgeBgColor: Int,
    val badgeTextColor: Int,
)

@Composable
private fun logLevelStyle(logLevel: Int): LogLevelStyle? = when (logLevel) {
    Log.ERROR -> LogLevelStyle(
        stringResource(R.string.log_item_error), R.drawable.ic_info_24px,
        R.color.log_error_icon, R.color.log_error_icon_bg, R.color.log_error_container, R.color.log_error_text
    )

    Log.WARN -> LogLevelStyle(
        stringResource(R.string.log_item_warn), R.drawable.ic_warning_24px,
        R.color.log_warning_icon, R.color.log_warning_icon_bg, R.color.log_warning_container, R.color.log_warning_text
    )

    Log.INFO -> LogLevelStyle(
        stringResource(R.string.log_item_info), R.drawable.ic_info_24px,
        R.color.log_info_icon, R.color.log_info_icon_bg, R.color.log_info_container, R.color.log_info_text
    )

    Log.DEBUG -> LogLevelStyle(
        stringResource(R.string.log_item_debug), R.drawable.ic_bug_report_24px,
        R.color.log_debug_icon, R.color.log_debug_icon_bg, R.color.log_debug_container, R.color.log_debug_text
    )

    Log.VERBOSE -> LogLevelStyle(
        stringResource(R.string.log_item_verbose), R.drawable.ic_bug_report_24px,
        R.color.log_debug_icon, R.color.log_debug_icon_bg, R.color.log_debug_container, R.color.log_debug_text
    )

    else -> null
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
                        color = style?.let { colorResource(it.iconBgColor) } ?: MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                style?.let {
                    Icon(
                        painter = painterResource(it.icon),
                        contentDescription = null,
                        tint = colorResource(it.iconColor),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = MaterialTheme.spacing.large)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    style?.let {
                        Text(
                            text = it.label.uppercase(),
                            fontSize = 10.sp,
                            color = colorResource(it.badgeTextColor),
                            modifier = Modifier
                                .background(colorResource(it.badgeBgColor), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = dateText,
                        fontSize = 12.sp,
                        color = colorResource(R.color.text_tertiary),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                    if (!ignoreTagChip) {
                        Text(
                            text = data.tag,
                            fontSize = 10.sp,
                            color = colorResource(R.color.on_secondary_container),
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .background(colorResource(R.color.secondary_container), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = data.text,
                    fontSize = 14.sp,
                    color = colorResource(R.color.text_primary),
                    modifier = Modifier.padding(top = MaterialTheme.spacing.xs)
                )
            }
        }
    }
}
