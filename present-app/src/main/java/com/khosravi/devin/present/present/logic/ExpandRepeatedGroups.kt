package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.RepeatedTextLogChildItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItemData

/**
 * Follows every group whose [ReplicatedTextLogItemData.groupKey] is in [expandedKeys] with one child per copy, in order,
 * so the list stays flat. Keys that match no group add nothing.
 */
fun List<LogItemData>.expandRepeatedGroups(expandedKeys: Set<String>): List<LogItemData> {
    if (expandedKeys.isEmpty()) return this
    val result = ArrayList<LogItemData>(size)
    for (item in this) {
        result += item
        if (item is ReplicatedTextLogItemData && item.groupKey in expandedKeys) {
            item.list.forEach { result += RepeatedTextLogChildItemData(item.groupKey, it) }
        }
    }
    return result
}
