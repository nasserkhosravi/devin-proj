package com.khosravi.devin.present.filter

import com.khosravi.devin.present.tool.NotEmptyString

//filters from logs
class TagFilterItem(
    val tagValue: String,
    isPinned: Boolean
) : FilterItem {

    override val id: String
        get() = tagValue

    override val ui: FilterUiData = FilterUiData(tagValue, NotEmptyString(tagValue), isPinned)

    fun copy(tagValue: String = this.tagValue, isPinned: Boolean = ui.isPinned): TagFilterItem {
        return TagFilterItem(tagValue, isPinned)
    }

}
