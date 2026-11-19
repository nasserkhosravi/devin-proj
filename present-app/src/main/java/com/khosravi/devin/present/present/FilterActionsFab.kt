package com.khosravi.devin.present.present

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.khosravi.devin.present.R
import com.khosravi.devin.present.uikit.theme.spacing

/**
 * A FAB that expands into the labelled [entries], stacked above it. Material3 1.4.0 has no stable
 * FloatingActionButtonMenu, so the menu is built from extended FABs. The scrim behind it is drawn by [LogScreen].
 */
@Composable
fun FilterActionsFab(
    entries: List<FilterMenuEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back only collapses the menu; the screen stays.
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                // Gap between the last item and the main FAB.
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.xs),
            ) {
                entries.forEach { entry ->
                    val label = stringResource(entry.label)
                    ExtendedFloatingActionButton(
                        onClick = {
                            onExpandedChange(false)
                            entry.onClick()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        // ExtendedFloatingActionButton hides its text from accessibility and takes the button's
                        // label from the icon, so the icon carries the label.
                        icon = { Icon(entry.icon(), contentDescription = label) },
                        text = { Text(label) },
                    )
                }
            }
        }
        FloatingActionButton(onClick = { onExpandedChange(!expanded) }) {
            if (expanded) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.filter_actions_close))
            } else {
                Icon(painterResource(R.drawable.ic_filter_list_24px), contentDescription = stringResource(R.string.filter_actions_open))
            }
        }
    }
}
