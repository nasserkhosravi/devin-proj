package com.khosravi.devin.present.present

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

/** Actions from a chip's long-press menu and from the selection bar ([CreateFilter]); see [LogScreen]. */
sealed interface FilterChipAction {
    data class CreateFilter(val tags: List<String>) : FilterChipAction
    data class TogglePin(val item: FilterItem) : FilterChipAction
    data class ShareAsJson(val item: TagFilterItem) : FilterChipAction
    data class Remove(val items: List<CustomFilterItem>) : FilterChipAction
}

private data class ChipColors(val container: Color, val content: Color)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterChipsRow(
    filters: List<FilterItem>,
    selectedId: String?,
    enabled: Boolean,
    /** Ids of the tag chips picked in selection mode; null when not in selection mode. */
    selection: Set<String>?,
    onSelect: (FilterItem) -> Unit,
    /** An action picked from a chip's long-press menu. */
    onAction: (FilterChipAction) -> Unit,
    /** "Select multiple" in a tag chip's menu: enters selection mode with that tag picked. */
    onStartSelection: (TagFilterItem) -> Unit,
    /** Tapping a tag chip in selection mode. */
    onToggleSelection: (TagFilterItem) -> Unit,
    onSearchOpen: () -> Unit,
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

    val chip: @Composable (FilterItem) -> Unit = { filter ->
        val inSelection = selection != null
        val isPicked = selection?.contains(filter.id) == true
        FilterChipView(
            filter = filter,
            isSelected = if (inSelection) isPicked else filter.id == selectedId,
            isChecked = isPicked,
            // Only tag chips can be picked; "All" and custom filters are dimmed and inert in selection mode.
            enabled = enabled && (!inSelection || filter is TagFilterItem),
            hasMenu = !inSelection && !filter.isIndexFilterItem(),
            onSelect = {
                if (inSelection) {
                    if (filter is TagFilterItem) onToggleSelection(filter)
                } else {
                    closeSearch()
                    onSelect(filter)
                }
            },
            onAction = onAction,
            onStartSelection = onStartSelection,
        )
    }

    // Decided on all filters, not the search matches: switching layouts while typing would
    // recreate the search chip and drop the keyboard.
    val rowCount = if (filters.size > TWO_ROWS_THRESHOLD) 2 else 1
    // Two rows show twice as many chips without scrolling, so a tag is easier to find. Chips alternate
    // between the rows by position, so none changes row when the search chip widens or the matches change.
    // Not lazy: every chip is composed, which is fine for a few hundred tags.
    Column(
        modifier = Modifier
            .padding(vertical = MaterialTheme.spacing.xs)
            .horizontalScroll(rememberScrollState()),
    ) {
        repeat(rowCount) { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row == 0) {
                    TagSearchChip(
                        isOpen = isSearchOpen,
                        query = query,
                        onOpen = {
                            isSearchOpen = true
                            onSearchOpen()
                        },
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
                }
                // The search chip is position 0, so filter i is at position i + 1.
                visibleFilters.forEachIndexed { index, filter ->
                    if ((index + 1) % rowCount == row) {
                        // Ids are not unique: a custom filter may be titled like a tag, "okhttp", "Index" or
                        // another custom filter, so qualify the key with type and position.
                        key("$index-${filter::class.simpleName}-${filter.id}") { chip(filter) }
                    }
                }
            }
        }
    }
}

// More chips than this are laid out in two rows.
private const val TWO_ROWS_THRESHOLD = 4
private val ICON_SIZE = 24.dp
private const val DISABLED_ALPHA = 0.38f

/**
 * A chip with a search icon that grows in place into a text field narrowing the chips. Same size and
 * padding as [FilterChipView]; the field widens with the typed text.
 */
@Composable
private fun TagSearchChip(
    isOpen: Boolean,
    query: String,
    onOpen: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.devinColors
    val pillShape = RoundedCornerShape(50)
    val focusRequester = remember { FocusRequester() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xs)
            .clip(pillShape)
            .background(colors.chipUnselected, pillShape)
            // Added only while closed: a disabled clickable also makes the text field below unfocusable.
            .then(
                if (isOpen) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, pillShape)
                else Modifier.clickable(onClick = onOpen)
            )
            // Animates the width only; the height stays the chip height in both states.
            .animateContentSize()
            .padding(horizontal = 12.dp, vertical = MaterialTheme.spacing.xs),
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = if (isOpen) null else stringResource(R.string.tag_search_open),
            tint = colors.chipTextUnselected,
            modifier = Modifier.size(ICON_SIZE),
        )
        if (isOpen) {
            val textStyle = MaterialTheme.typography.bodyLarge
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .padding(start = MaterialTheme.spacing.xs)
                    // Max: as wide as the hint while empty, then as wide as the typed text.
                    .width(IntrinsicSize.Max)
                    .widthIn(max = 180.dp),
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tag_search_hint),
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = textStyle.copy(color = colors.chipTextUnselected),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                // Runs once the field is in the composition, so the focus request has a target.
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.tag_search_close),
                tint = colors.chipTextUnselected,
                modifier = Modifier
                    .padding(start = MaterialTheme.spacing.xs)
                    .size(ICON_SIZE)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
            )
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
    isChecked: Boolean,
    enabled: Boolean,
    hasMenu: Boolean,
    onSelect: () -> Unit,
    onAction: (FilterChipAction) -> Unit,
    onStartSelection: (TagFilterItem) -> Unit,
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xs)
            .background(chipColors.container, pillShape)
            .clip(pillShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onSelect,
                onLongClick = if (hasMenu) {
                    { menuOpen = true }
                } else null,
            )
            .padding(horizontal = 12.dp, vertical = MaterialTheme.spacing.xs)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
    ) {
        if (isChecked) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = chipColors.content,
                modifier = Modifier
                    .padding(end = MaterialTheme.spacing.xs)
                    .size(18.dp)
            )
        }
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
            val item: @Composable (Int, () -> Unit) -> Unit = { label, action ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    menuOpen = false
                    action()
                })
            }
            item(if (filter.ui.isPinned) R.string.menu_action_unpin else R.string.menu_action_pin) {
                onAction(FilterChipAction.TogglePin(filter))
            }
            if (filter is TagFilterItem) {
                item(R.string.menu_action_share_as_json) { onAction(FilterChipAction.ShareAsJson(filter)) }
                item(R.string.menu_action_select_multiple) { onStartSelection(filter) }
            }
            if (filter is CustomFilterItem) {
                item(R.string.menu_action_remove) { onAction(FilterChipAction.Remove(listOf(filter))) }
            }
        }
    }
}
