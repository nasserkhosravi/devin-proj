package com.khosravi.devin.present.present

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.log.LogItemData
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogScreen(
    calendar: CalendarProxy,
    filters: List<FilterItem>,
    selectedFilterId: String?,
    ignoreTagChip: Boolean,
    isFilterRowEnabled: Boolean,
    logItems: List<LogListRowItem>,
    isLoading: Boolean,
    onSelectFilter: (FilterItem) -> Unit,
    onFilterAction: (FilterChipAction) -> Unit,
    onTagSearchOpen: () -> Unit,
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
    onOpenSettings: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Tag ids picked in selection mode ("Select multiple"; null = not in it); an ArrayList so it survives rotation.
    var selectionIds by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    // In pick order. Drop picks whose chip is gone (refreshed tags); leaving the last one ends selection mode.
    val selectedTags = remember(filters, selectionIds) {
        selectionIds?.let { ids ->
            val tags = filters.filterIsInstance<TagFilterItem>().associateBy { it.id }
            ids.mapNotNull { tags[it] }
        }
    }
    LaunchedEffect(selectedTags) {
        if (selectedTags != null && selectedTags.isEmpty()) selectionIds = null
    }
    fun exitSelection() {
        selectionIds = null
    }
    BackHandler(enabled = selectionIds != null) { exitSelection() }

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
        // Edge-to-edge: the window no longer resizes for the keyboard, so keep the content above it too.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        topBar = {
            if (selectedTags != null) {
                FilterSelectionBar(
                    count = selectedTags.size,
                    onClose = ::exitSelection,
                    onCreateFilter = {
                        exitSelection()
                        onFilterAction(FilterChipAction.CreateFilter(selectedTags.map { it.tagValue }))
                    },
                )
            } else TopAppBar(
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
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_settings)) }, onClick = {
                            overflowOpen = false
                            onOpenSettings()
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
                    selection = selectionIds?.toSet(),
                    onSelect = onSelectFilter,
                    onAction = onFilterAction,
                    onStartSelection = { selectionIds = arrayListOf(it.id) },
                    onToggleSelection = { tag ->
                        val ids = selectionIds ?: arrayListOf()
                        selectionIds = ArrayList(if (tag.id in ids) ids - tag.id else ids + tag.id)
                    },
                    onSearchOpen = onTagSearchOpen,
                )
                // The search field (always first when present) is a sticky header, so it stays reachable while scrolling.
                val searchItem = logItems.firstOrNull() as? LogListRowItem.Search
                val rowItems = remember(logItems) { logItems.filterIsInstance<LogListRowItem.Row>() }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (searchItem != null) {
                        stickyHeader(key = "search") {
                            // Opaque, so rows scrolling underneath don't show through.
                            Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                                SearchBarRow(
                                    sessionId = searchItem.sessionId,
                                    text = searchItem.text,
                                    hint = searchItem.hint,
                                    onTextChange = onSearchTextChange,
                                )
                            }
                        }
                    }
                    // No stable content id exists across all LogItemData subtypes (some carry one,
                    // e.g. HttpLogItemData.logId, others don't), so this key is only a synthetic
                    // position+type marker — better than the implicit positional default, but not
                    // true content-identity diffing.
                    itemsIndexed(rowItems, key = { index, rowItem -> "$index-${rowItem.data::class.simpleName}" }) { index, rowItem ->
                        val previous = rowItems.getOrNull(index - 1)?.data
                        LogItemDataRow(calendar, rowItem.data, previous, ignoreTagChip, onTextLogClick, onHttpLogClick)
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Replaces the top bar in tag selection mode: "N selected", close, and Create filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSelectionBar(count: Int, onClose: () -> Unit, onCreateFilter: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.selection_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.selection_close))
            }
        },
        actions = {
            TextButton(onClick = onCreateFilter) {
                Text(stringResource(R.string.selection_create_filter))
            }
        }
    )
}

/**
 * One log row for a [LogItemData]. Shared by [LogScreen] and [ImportLogScreen].
 * [ignoreTagChip] hides the tag chip on text rows; set it when every row has the same tag.
 * [previous] is the row above; a row right below a session start skips its top divider, since that row is one.
 */
@Composable
internal fun LogItemDataRow(
    calendar: CalendarProxy,
    data: LogItemData,
    previous: LogItemData?,
    ignoreTagChip: Boolean,
    onTextLogClick: (TextLogItemData) -> Unit,
    onHttpLogClick: (HttpLogItemData) -> Unit,
) {
    val showDivider = previous !is SessionStartLogItemData
    when (data) {
        is DateLogItemData -> DateHeaderRow(calendar, data)
        is TextLogItemData -> TextLogRow(calendar, data, ignoreTagChip = ignoreTagChip, onClick = { onTextLogClick(data) }, showDivider = showDivider)
        is HttpLogItemData -> HttpLogRow(calendar, data, onClick = { onHttpLogClick(data) }, showDivider = showDivider)
        is ImageLogItemData -> ImageLogRow(calendar, data, showDivider = showDivider)
        is SessionStartLogItemData -> SessionStartRow(calendar, data)
        is ReplicatedTextLogItemData -> Unit // never emitted by ReaderViewModel; exhaustiveness guard only
    }
}
