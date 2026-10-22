package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.uikit.theme.spacing

/**
 * Logs read from an imported JSON file, with a "contains" search field above them.
 * [items] is null while the file is being read and converted.
 */
@Composable
fun ImportLogScreen(
    calendar: CalendarProxy,
    items: List<LogItemData>?,
    onTextLogClick: (TextLogItemData) -> Unit,
    onHttpLogClick: (HttpLogItemData) -> Unit,
) {
    // Saveable so the query survives a trip to the detail destination and back.
    var query by rememberSaveable { mutableStateOf("") }
    val visibleItems = remember(items, query) { items?.filterByText(query) }

    Scaffold(
        // Edge-to-edge: the window no longer resizes for the keyboard, so keep the content above it too.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.field_contain_value)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.small),
            )
            if (visibleItems == null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Same synthetic position+type key as LogScreen: LogItemData has no common stable id.
                    itemsIndexed(visibleItems, key = { index, item -> "$index-${item::class.simpleName}" }) { _, item ->
                        LogItemDataRow(calendar, item, ignoreTagChip = false, onTextLogClick, onHttpLogClick)
                    }
                }
            }
        }
    }
}

/** Text logs are kept when their text contains [query] (ignoring case); every other row is always kept. */
private fun List<LogItemData>.filterByText(query: String): List<LogItemData> {
    if (query.isEmpty()) return this
    return filter { item ->
        val text = when (item) {
            is TextLogItemData -> item.text
            is ReplicatedTextLogItemData -> item.text
            else -> null
        }
        text?.contains(query, ignoreCase = true) ?: true
    }
}
