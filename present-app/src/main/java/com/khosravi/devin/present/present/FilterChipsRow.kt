package com.khosravi.devin.present.present

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.filter.isIndexFilterItem
import com.khosravi.devin.present.uikit.theme.devinColors
import com.khosravi.devin.present.uikit.theme.spacing
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi

sealed interface FilterChipAction {
    data class TogglePin(val item: FilterItem) : FilterChipAction
    data class ShareAsJson(val item: TagFilterItem) : FilterChipAction
    data class Remove(val item: CustomFilterItem) : FilterChipAction
}

private data class ChipColors(val container: Color, val content: Color)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterChipsRow(
    filters: List<FilterItem>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (FilterItem) -> Unit,
    onAction: (FilterChipAction) -> Unit,
) {
    // Not saved: the tag search is cleared on rotation and when the screen is left.
    var isSearchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    fun closeSearch() {
        isSearchOpen = false
        query = ""
    }

    BackHandler(enabled = isSearchOpen) { closeSearch() }

    val visibleFilters = remember(filters, query) {
        if (query.isBlank()) filters
        else filters.filter { !it.isIndexFilterItem() && chipLabel(it).contains(query.trim(), ignoreCase = true) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // As tall as the search icon button, so the row doesn't jump when the search opens or closes.
        modifier = Modifier
            .padding(vertical = MaterialTheme.spacing.xs)
            .heightIn(min = 48.dp),
    ) {
        // Outside the LazyRow, so it stays visible while the chips scroll.
        TagSearchField(
            isOpen = isSearchOpen,
            query = query,
            onOpen = { isSearchOpen = true },
            onQueryChange = { query = it },
            onClose = ::closeSearch,
        )
        if (visibleFilters.isEmpty()) {
            Text(
                text = stringResource(R.string.tag_search_no_match),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
            )
        }
        LazyRow {
            // Ids are not unique: a custom filter may be titled like a tag, "okhttp", "Index" or another
            // custom filter, and the list keeps such duplicates. Qualify the key with type and position
            // so Compose never sees a duplicate key (which would throw).
            itemsIndexed(visibleFilters, key = { index, filter -> "$index-${filter::class.simpleName}-${filter.id}" }) { _, filter ->
                FilterChipView(
                    filter = filter,
                    isSelected = filter.id == selectedId,
                    enabled = enabled,
                    onSelect = {
                        closeSearch()
                        onSelect(filter)
                    },
                    onAction = onAction,
                )
            }
        }
    }
}

/** A search icon that expands in place into a small text field narrowing the chips. */
@Composable
private fun TagSearchField(
    isOpen: Boolean,
    query: String,
    onOpen: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (!isOpen) {
        IconButton(onClick = onOpen) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.tag_search_open))
        }
    }
    AnimatedVisibility(visible = isOpen, enter = expandHorizontally(), exit = shrinkHorizontally()) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        val pillShape = RoundedCornerShape(50)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = MaterialTheme.spacing.xs)
                .width(160.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary, pillShape)
                .padding(start = 12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tag_search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.tag_search_close),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun chipLabel(filter: FilterItem): String {
    val isOkHttp = filter is TagFilterItem && filter.tagValue == DevinHttpFlagsApi.LOG_TAG
    return if (isOkHttp) "http" else filter.ui.title.value
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterChipView(
    filter: FilterItem,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onAction: (FilterChipAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isOkHttp = filter is TagFilterItem && filter.tagValue == DevinHttpFlagsApi.LOG_TAG
    val label = chipLabel(filter)

    val colors = MaterialTheme.devinColors
    val chipColors = if (isOkHttp) {
        if (isSelected) {
            ChipColors(container = colors.okHttpChipSelected, content = colors.okHttpChipTextSelected)
        } else {
            ChipColors(container = colors.okHttpChipUnselected, content = colors.okHttpChipTextUnselected)
        }
    } else {
        if (isSelected) {
            ChipColors(container = colors.chipSelected, content = colors.chipTextSelected)
        } else {
            ChipColors(container = colors.chipUnselected, content = colors.chipTextUnselected)
        }
    }

    val pillShape = RoundedCornerShape(50)
    val onLongClick: (() -> Unit)? = if (filter.isIndexFilterItem()) {
        null
    } else {
        { menuOpen = true }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xs)
            .background(chipColors.container, pillShape)
            .clip(pillShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onSelect,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = MaterialTheme.spacing.xs)
    ) {
        if (filter.ui.isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_keep_24px),
                contentDescription = null,
                tint = chipColors.content,
                modifier = Modifier.padding(end = MaterialTheme.spacing.xs)
            )
        }
        if (filter is TagFilterItem && filter.hasUnseenError) {
            Box(
                modifier = Modifier
                    .padding(end = MaterialTheme.spacing.xs)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
        }
        Text(text = label, color = chipColors.content)

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (filter.ui.isPinned) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_unpin)) }, onClick = {
                    menuOpen = false
                    onAction(FilterChipAction.TogglePin(filter))
                })
            } else {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_pin)) }, onClick = {
                    menuOpen = false
                    onAction(FilterChipAction.TogglePin(filter))
                })
            }
            if (filter is TagFilterItem) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_share_as_json)) }, onClick = {
                    menuOpen = false
                    onAction(FilterChipAction.ShareAsJson(filter))
                })
            }
            if (filter is CustomFilterItem) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_remove)) }, onClick = {
                    menuOpen = false
                    onAction(FilterChipAction.Remove(filter))
                })
            }
        }
    }
}
