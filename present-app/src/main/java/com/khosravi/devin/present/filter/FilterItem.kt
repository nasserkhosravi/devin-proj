package com.khosravi.devin.present.filter

import com.khosravi.devin.present.tool.NotEmptyString

interface FilterItem {
    val id: String
    val ui: FilterUiData
}

data class FilterUiData(
    val id: String,
    val title: NotEmptyString,
    val isPinned: Boolean
)

data class CustomFilterItem(
    override val ui: FilterUiData,
    val criteria: CustomFilterCriteria
) : FilterItem {
    override val id: String
        get() = ui.title.value
}


fun FilterItem.setIsPinned(isPinned: Boolean): FilterItem {
    return when (this) {
        is CustomFilterItem -> copy(ui = ui.copy(isPinned = isPinned))
        is TagFilterItem -> copy(isPinned = isPinned)
        is IndexFilterItem -> this

        else -> {
            throw UnsupportedOperationException()
        }
    }
}

fun FilterItem.isIndexFilterItem() = this is IndexFilterItem