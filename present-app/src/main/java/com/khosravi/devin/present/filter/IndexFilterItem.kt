package com.khosravi.devin.present.filter

import com.khosravi.devin.present.tool.NotEmptyString

class IndexFilterItem(
    override val id: String = ID
) : FilterItem {

    override val ui: FilterUiData = FilterUiData(id, title = NotEmptyString(id))

    override val criteria: FilterCriteria? = null

    companion object {
        const val ID = "Index"
    }
}