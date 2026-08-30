package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.log.LogListRowItem
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.SessionStartLogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.present.logitem.DateHeaderRow
import com.khosravi.devin.present.present.logitem.HttpLogRow
import com.khosravi.devin.present.present.logitem.ImageLogRow
import com.khosravi.devin.present.present.logitem.SearchBarRow
import com.khosravi.devin.present.present.logitem.SessionStartRow
import com.khosravi.devin.present.present.logitem.TextLogRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    calendar: CalendarProxy,
    filters: List<FilterItem>,
    selectedFilterId: String?,
    isFilterRowEnabled: Boolean,
    logItems: List<LogListRowItem>,
    isLoading: Boolean,
    onSelectFilter: (FilterItem) -> Unit,
    onFilterAction: (FilterChipAction) -> Unit,
    onSearchTextChange: (String?) -> Unit,
    onTextLogClick: (TextLogItemData) -> Unit,
    onHttpLogClick: (HttpLogItemData) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit,
    onClearFilters: () -> Unit,
    onCreateFilter: () -> Unit,
    onExport: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Fires on every scroll frame while near the end, not just once — callers of onLoadMore() must
    // debounce/guard against duplicate calls themselves (see LogActivity's LogPaginationState).
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 3) {
                onLoadMore()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onClearLogs) {
                        Icon(painterResource(R.drawable.baseline_clear_24), contentDescription = stringResource(R.string.menu_clear_logs))
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(painterResource(R.drawable.baseline_refresh_24), contentDescription = stringResource(R.string.menu_refresh))
                    }
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_export_logs)) }, onClick = {
                            overflowOpen = false
                            onExport()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_create_filter)) }, onClick = {
                            overflowOpen = false
                            onCreateFilter()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_clear_filters)) }, onClick = {
                            overflowOpen = false
                            onClearFilters()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_toggle_theme)) }, onClick = {
                            overflowOpen = false
                            onToggleTheme()
                        })
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FilterChipsRow(
                    filters = filters,
                    selectedId = selectedFilterId,
                    enabled = isFilterRowEnabled,
                    onSelect = onSelectFilter,
                    onAction = onFilterAction,
                )
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    // No stable content id exists across all LogItemData subtypes (some carry one,
                    // e.g. HttpLogItemData.logId, others don't), so this key is only a synthetic
                    // position+type marker — better than the implicit positional default, but not
                    // true content-identity diffing.
                    itemsIndexed(logItems, key = { index, rowItem -> "$index-${rowItem::class.simpleName}" }) { _, rowItem ->
                        LogListRow(calendar, rowItem, onSearchTextChange, onTextLogClick, onHttpLogClick)
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun LogListRow(
    calendar: CalendarProxy,
    rowItem: LogListRowItem,
    onSearchTextChange: (String?) -> Unit,
    onTextLogClick: (TextLogItemData) -> Unit,
    onHttpLogClick: (HttpLogItemData) -> Unit,
) {
    when (rowItem) {
        is LogListRowItem.Search -> SearchBarRow(sessionId = rowItem.sessionId, text = rowItem.text, hint = rowItem.hint, onTextChange = onSearchTextChange)
        is LogListRowItem.Row -> when (val d = rowItem.data) {
            is DateLogItemData -> DateHeaderRow(calendar, d)
            is TextLogItemData -> TextLogRow(calendar, d, ignoreTagChip = false, onClick = { onTextLogClick(d) })
            is HttpLogItemData -> HttpLogRow(calendar, d, onClick = { onHttpLogClick(d) })
            is ImageLogItemData -> ImageLogRow(calendar, d)
            is SessionStartLogItemData -> SessionStartRow(calendar, d)
            is ReplicatedTextLogItemData -> Unit // never emitted by ReaderViewModel; exhaustiveness guard only
        }
    }
}
