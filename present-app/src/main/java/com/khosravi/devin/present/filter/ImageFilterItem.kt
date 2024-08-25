package com.khosravi.devin.present.filter

import android.graphics.Color
import com.khosravi.devin.present.tool.NotEmptyString
import com.khosravi.devin.write.api.DevinImageFlagsApi

class ImageFilterItem(
    override val id: String = ID
) : FilterItem {

    companion object {
        const val ID = DevinImageFlagsApi.LOG_TAG
    }

    override val ui: FilterUiData = FilterUiData(id, title = NotEmptyString("Image"), ChipColor(Color.WHITE, Color.BLACK))
    override val criteria: FilterCriteria? = null
}