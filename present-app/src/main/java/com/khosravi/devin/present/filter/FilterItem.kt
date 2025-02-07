package com.khosravi.devin.present.filter

import com.khosravi.devin.present.tool.NotEmptyString

interface FilterItem {
    val id: String
    val ui: FilterUiData
}

class FilterUiData(
    val id: String,
    val title: NotEmptyString,
    val chipColor: ChipColor,
)

class CustomFilterItem(
    override val ui: FilterUiData,
    val criteria: CustomFilterCriteria?
) : FilterItem {
    override val id: String
        get() = ui.title.value
}
