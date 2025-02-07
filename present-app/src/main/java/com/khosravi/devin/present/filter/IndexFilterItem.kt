package com.khosravi.devin.present.filter

import com.khosravi.devin.present.Defaults
import com.khosravi.devin.present.tool.NotEmptyString

class IndexFilterItem(
    override val id: String = ID
) : FilterItem {

    override val ui: FilterUiData = FilterUiData(id, title = NotEmptyString(id), Defaults.filterColor)

    companion object {
        const val ID = "Index"
    }
}