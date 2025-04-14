package com.khosravi.devin.present.filter

import com.khosravi.devin.present.Defaults
import com.khosravi.devin.present.tool.NotEmptyString
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi

class HttpFilterItem(
    override val id: String = ID
) : FilterItem {

    override val ui: FilterUiData = FilterUiData(id, title = NotEmptyString("Http"), Defaults.filterColor)

    companion object {
        const val ID = DevinHttpFlagsApi.LOG_TAG
    }
}