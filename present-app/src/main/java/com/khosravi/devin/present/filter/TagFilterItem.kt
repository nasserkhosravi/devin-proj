package com.khosravi.devin.present.filter

import com.khosravi.devin.present.tool.NotEmptyString

//filters from logs
class TagFilterItem(
    val tagValue: String,
    isPinned: Boolean,
    // The tag logged an error after the user last selected its chip; shown as a red dot.
    val hasUnseenError: Boolean = false,
) : FilterItem {

    override val id: String
        get() = tagValue

    override val ui: FilterUiData = FilterUiData(tagValue, NotEmptyString(tagValue), isPinned)

    fun copy(
        tagValue: String = this.tagValue,
        isPinned: Boolean = ui.isPinned,
        hasUnseenError: Boolean = this.hasUnseenError,
    ): TagFilterItem {
        return TagFilterItem(tagValue, isPinned, hasUnseenError)
    }

}
