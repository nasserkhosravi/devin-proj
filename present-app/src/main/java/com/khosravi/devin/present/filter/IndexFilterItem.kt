package com.khosravi.devin.present.filter

import com.khosravi.devin.present.Defaults
import com.khosravi.devin.present.tool.NotEmptyString

class IndexFilterItem(
    override val id: String = ID
) : FilterItem {

    override val ui: FilterUiData = FilterUiData(id, title = NotEmptyString("All"), Defaults.filterColor,true)

    companion object {
        const val ID = "Index"
        val instance = IndexFilterItem()
    }
}