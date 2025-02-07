package com.khosravi.devin.present.filter

import com.khosravi.devin.present.Defaults
import com.khosravi.devin.present.tool.NotEmptyString
import com.khosravi.devin.write.api.DevinImageFlagsApi

class ImageFilterItem(
    override val id: String = ID
) : FilterItem {

    companion object {
        const val ID = DevinImageFlagsApi.LOG_TAG
    }

    override val ui: FilterUiData = FilterUiData(id, title = NotEmptyString("Image"), Defaults.filterColor)
    override val criteria: FilterCriteria? = null
}