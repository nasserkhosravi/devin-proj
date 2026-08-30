package com.khosravi.devin.present.present

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.filter.isIndexFilterItem
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
    LazyRow(modifier = Modifier.padding(vertical = MaterialTheme.spacing.xs)) {
        items(filters, key = { it.id }) { filter ->
            FilterChipView(
                filter = filter,
                isSelected = filter.id == selectedId,
                enabled = enabled,
                onSelect = { onSelect(filter) },
                onAction = onAction,
            )
        }
    }
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
    val label = if (isOkHttp) "http" else filter.ui.title.value

    val chipColors = if (isOkHttp) {
        if (isSelected) {
            ChipColors(
                container = colorResource(R.color.tag_okhttp_container_selected),
                content = colorResource(R.color.tag_okhttp_text_selected),
            )
        } else {
            ChipColors(
                container = colorResource(R.color.tag_okhttp_container_unselected),
                content = colorResource(R.color.tag_okhttp_text_unselected),
            )
        }
    } else {
        if (isSelected) {
            ChipColors(
                container = colorResource(R.color.chip_selected),
                content = colorResource(R.color.chip_text_selected),
            )
        } else {
            ChipColors(
                container = colorResource(R.color.chip_unselected),
                content = colorResource(R.color.chip_text_unselected),
            )
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
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (filter.ui.isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_keep_24px),
                contentDescription = null,
                tint = chipColors.content,
                modifier = Modifier.padding(end = 4.dp)
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
