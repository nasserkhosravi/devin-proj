package com.khosravi.devin.present.filter

import com.khosravi.devin.present.Defaults
import com.khosravi.devin.present.tool.NotEmptyString

//filters from logs
class TagFilterItem(
    val tagValue: String,
) : FilterItem {
    override val id: String
        get() = tagValue
    override val criteria: FilterCriteria = FilterCriteria(tagValue, null)

    override val ui: FilterUiData = FilterUiData(tagValue, NotEmptyString(tagValue), Defaults.filterColor)
}