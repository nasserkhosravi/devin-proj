package com.khosravi.devin.present.filter

import com.khosravi.devin.present.Defaults
import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.tool.NotEmptyString

//filters from logs
class TagFilterItem(
    val tagValue: String,
) : FilterItem {
    override val id: String
        get() = tagValue

    override val ui: FilterUiData = FilterUiData(tagValue, NotEmptyString(tagValue), Defaults.filterColor)
}

fun TagFilterItem.createCriteria(): FilterCriteria {

    return object : FilterCriteria {

        override fun applyCriteria(logs: List<LogData>): List<LogData> {
            return logs.filter { it.tag == tagValue }
        }

    }
}