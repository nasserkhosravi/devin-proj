package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.NewLogsDividerItemData
import com.khosravi.devin.present.log.SessionStartLogItemData
import com.khosravi.devin.present.log.logIdOrNull

/**
 * Inserts a [NewLogsDividerItemData] before the first log that is not newer than [boundaryId], when logs above it are.
 * Session starts don't count as new and never take the divider's place: they open a block, so they stay above it.
 * With a null [boundaryId], or nothing newer, the list is returned as it is.
 */
fun List<LogItemData>.withNewLogsDivider(boundaryId: Long?): List<LogItemData> {
    if (boundaryId == null) return this
    var newCount = 0
    for ((index, row) in withIndex()) {
        if (row is SessionStartLogItemData) continue
        val id = row.logIdOrNull() ?: continue
        if (id > boundaryId) {
            newCount++
        } else {
            if (newCount == 0) return this
            return take(index) + NewLogsDividerItemData(newCount) + drop(index)
        }
    }
    return this
}
