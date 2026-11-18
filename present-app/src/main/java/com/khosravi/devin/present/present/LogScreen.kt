package com.khosravi.devin.present.present

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import com.khosravi.devin.present.present.logitem.RepeatedTextLogChildRow
import com.khosravi.devin.present.present.logitem.RepeatedTextLogRow
import com.khosravi.devin.present.present.logitem.SessionStartRow
import com.khosravi.devin.present.present.logic.CountingReplicatedTextLogItemDataOperation
import com.khosravi.devin.present.present.logic.expandRepeatedGroups
import com.khosravi.devin.present.log.RepeatedTextLogChildItemData
import com.khosravi.devin.present.present.logitem.TextLogRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun LogScreen(
    calendar: CalendarProxy,
    filters: List<FilterItem>,
    selectedFilterId: String?,
    ignoreTagChip: Boolean,
    groupRepeatedLogs: Boolean,
    isFilterRowEnabled: Boolean,
    logItems: List<LogListRowItem>,
    isLoading: Boolean,
    isRefreshing: Boolean,
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
    // Keys of expanded repeated-log groups; an ArrayList so it survives rotation and the trip to the detail screen.
    var expandedKeys by rememberSaveable { mutableStateOf(ArrayList<String>()) }
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
        // Pulling is off in tag selection mode, where a drag must not refresh.
        val pullState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefresh)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullState, enabled = selectedTags == null)
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
                // The search field (always first when present) stays above the list, so it is reachable while scrolling.
                val searchItem = logItems.firstOrNull() as? LogListRowItem.Search
                val rowItems = remember(logItems, groupRepeatedLogs, expandedKeys) {
                    val rows = logItems.filterIsInstance<LogListRowItem.Row>().sessionStartsFirst()
                    if (!groupRepeatedLogs) rows
                    else rows.map { it.data }
                        .groupRepeatedAndExpand(expandedKeys.toSet())
                        .map { LogListRowItem.Row(it) }
                }
                val onGroupClick = { group: ReplicatedTextLogItemData ->
                    expandedKeys = ArrayList(if (group.groupKey in expandedKeys) expandedKeys - group.groupKey else expandedKeys + group.groupKey)
                }
                if (searchItem != null) {
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
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // No stable content id exists across all LogItemData subtypes (some carry one,
                    // e.g. HttpLogItemData.logId, others don't), so this key is only a synthetic
                    // position+type marker — better than the implicit positional default, but not
                    // true content-identity diffing.
                    rowItems.forEachIndexed { index, rowItem ->
                        val key = "$index-${rowItem.data::class.simpleName}"
                        val previous = rowItems.getOrNull(index - 1)?.data
                        if (rowItem.data is SessionStartLogItemData) {
                            // Opens its session's block, so it stays pinned while that session's logs scroll under it.
                            stickyHeader(key = key) {
                                // Opaque, so rows scrolling underneath don't show through.
                                Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                                    LogItemDataRow(calendar, rowItem.data, previous, ignoreTagChip, expandedKeys.toSet(), onGroupClick, onTextLogClick, onHttpLogClick)
                                }
                            }
                        } else {
                            item(key = key) {
                                LogItemDataRow(calendar, rowItem.data, previous, ignoreTagChip, expandedKeys.toSet(), onGroupClick, onTextLogClick, onHttpLogClick)
                            }
                        }
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            PullRefreshIndicator(refreshing = isRefreshing, state = pullState, modifier = Modifier.align(Alignment.TopCenter))
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
    expandedGroupKeys: Set<String>,
    onGroupClick: (ReplicatedTextLogItemData) -> Unit,
    onTextLogClick: (TextLogItemData) -> Unit,
    onHttpLogClick: (HttpLogItemData) -> Unit,
) {
    val showDivider = previous !is SessionStartLogItemData
    when (data) {
        is DateLogItemData -> DateHeaderRow(calendar, data)
        is TextLogItemData -> TextLogRow(calendar, data, ignoreTagChip = ignoreTagChip, onClick = { onTextLogClick(data) }, showDivider = showDivider)
        is HttpLogItemData -> HttpLogRow(calendar, data, onClick = { onHttpLogClick(data) }, showDivider = showDivider)
        is ImageLogItemData -> ImageLogRow(calendar, data, showDivider = showDivider)
        is SessionStartLogItemData -> SessionStartRow(calendar, data, onClick = { onTextLogClick(data.detail) })
        is ReplicatedTextLogItemData -> RepeatedTextLogRow(
            calendar, data, isExpanded = data.groupKey in expandedGroupKeys, ignoreTagChip = ignoreTagChip,
            onClick = { onGroupClick(data) }, showDivider = showDivider,
        )
        is RepeatedTextLogChildItemData -> RepeatedTextLogChildRow(calendar, data, onClick = { onTextLogClick(data.log) })
    }
}

/**
 * Logs run newest first, so a session start row comes after the logs of its own session. Moves each one in front of
 * them, so it opens its block (and can be pinned above it); logs above the first session start stay as they are.
 */
private fun List<LogListRowItem.Row>.sessionStartsFirst(): List<LogListRowItem.Row> {
    val result = ArrayList<LogListRowItem.Row>(size)
    val block = ArrayList<LogListRowItem.Row>()
    for (row in this) {
        if (row.data is SessionStartLogItemData) {
            result += row
            result += block
            block.clear()
        } else {
            block += row
        }
    }
    result += block
    return result
}

/** Folds back-to-back repeats into groups, then adds the children of the expanded ones. Shared by both screens. */
internal fun List<LogItemData>.groupRepeatedAndExpand(expandedKeys: Set<String>): List<LogItemData> =
    CountingReplicatedTextLogItemDataOperation(this).get().expandRepeatedGroups(expandedKeys)
