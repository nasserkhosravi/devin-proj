package com.khosravi.devin.present.filter

import com.khosravi.devin.present.data.LogData

class CustomFilterCriteria(
    val tag: String?,
    val searchText: String?
) : FilterCriteria {

    override fun applyCriteria(logs: List<LogData>): List<LogData> {
        val searchTextFunc: ((logValue: String) -> Boolean)? = if (searchText.isNullOrEmpty()) {
            null
        } else {
            {
                it.contains(searchText, true)
            }
        }

        val tagFunc: ((logTag: String) -> Boolean)? = if (tag.isNullOrEmpty()) {
            null
        } else {
            {
                it.contains(tag, true)
            }
        }

        return logs.filter {
            val searchTextConditionResult = searchTextFunc?.invoke(it.value) ?: true
            val tagConditionResult = tagFunc?.invoke(it.tag) ?: true
            searchTextConditionResult && tagConditionResult
        }
    }

}