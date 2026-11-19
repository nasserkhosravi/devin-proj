package com.khosravi.devin.present.present

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.khosravi.devin.present.R
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.filter.isIndexFilterItem

/** [icon] is a composable so an entry can use either a Material icon or a drawable. */
data class FilterMenuEntry(
    @StringRes val label: Int,
    val icon: @Composable () -> Painter,
    val onClick: () -> Unit,
)

private fun vectorIcon(vector: ImageVector): @Composable () -> Painter = { rememberVectorPainter(vector) }

/** The "Create filter" entry the FAB adds after the filter's own actions. */
fun createFilterMenuEntry(onClick: () -> Unit) = FilterMenuEntry(R.string.menu_create_filter, vectorIcon(Icons.Default.Add), onClick)

/**
 * The filter's actions, in long-press-menu order. Shared by the chip menu and the filter actions FAB so they
 * can't drift apart. Empty for the index ("All") filter. A null [onStartSelection] leaves out "Select multiple".
 */
fun filterMenuEntries(
    filter: FilterItem,
    onAction: (FilterChipAction) -> Unit,
    onStartSelection: ((TagFilterItem) -> Unit)?,
): List<FilterMenuEntry> {
    if (filter.isIndexFilterItem()) return emptyList()
    return buildList {
        val isPinned = filter.ui.isPinned
        add(
            FilterMenuEntry(
                label = if (isPinned) R.string.menu_action_unpin else R.string.menu_action_pin,
                icon = { painterResource(if (isPinned) R.drawable.ic_keep_off_24px else R.drawable.ic_keep_24px) },
            ) { onAction(FilterChipAction.TogglePin(filter)) }
        )
        if (filter is TagFilterItem) {
            add(FilterMenuEntry(R.string.menu_action_share_as_json, vectorIcon(Icons.Default.Share)) { onAction(FilterChipAction.ShareAsJson(filter)) })
            if (onStartSelection != null) {
                add(FilterMenuEntry(R.string.menu_action_select_multiple, vectorIcon(Icons.Default.Check)) { onStartSelection(filter) })
            }
        }
        if (filter is CustomFilterItem) {
            add(FilterMenuEntry(R.string.menu_action_edit, vectorIcon(Icons.Default.Edit)) { onAction(FilterChipAction.Edit(filter)) })
            add(FilterMenuEntry(R.string.menu_action_remove, vectorIcon(Icons.Default.Delete)) { onAction(FilterChipAction.Remove(listOf(filter))) })
        }
    }
}
