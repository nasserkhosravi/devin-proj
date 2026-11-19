package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.khosravi.devin.present.R
import com.khosravi.devin.present.uikit.theme.spacing

/** An accent line with "N new logs above" in its middle, between the logs that arrived while away and the older ones. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewLogsDividerRow(count: Int) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Divider(modifier = Modifier.weight(1f), color = accent)
        Text(
            text = pluralStringResource(R.plurals.new_logs_divider, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
        )
        Divider(modifier = Modifier.weight(1f), color = accent)
    }
}
