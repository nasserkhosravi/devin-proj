package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import com.khosravi.devin.present.R
import com.khosravi.devin.present.uikit.theme.spacing

/** "↑ N new logs", shown over the list while the user is scrolled down; a tap scrolls to the new logs. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewLogsPill(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = pluralStringResource(R.plurals.new_logs_pill, count, count),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
    )
}
