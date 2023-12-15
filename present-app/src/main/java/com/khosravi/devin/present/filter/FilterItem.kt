package com.khosravi.devin.present.filter

import com.khosravi.devin.present.tool.NotEmptyString

interface FilterItem {
    val id: String
    val ui: FilterUiData
    val criteria: FilterCriteria?
}

class FilterCriteria(
    val type: String?,
    val searchText: String?
)

class FilterUiData(
    val id: String,
    val title: NotEmptyString
)

class DefaultFilterItem(
    override val ui: FilterUiData,
    override val criteria: FilterCriteria?
) : FilterItem {
    override val id: String
        get() = ui.title.value
}
