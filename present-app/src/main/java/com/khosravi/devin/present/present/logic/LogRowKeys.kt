package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.NewLogsDividerItemData
import com.khosravi.devin.present.log.RepeatedTextLogChildItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.SessionStartLogItemData
import com.khosravi.devin.present.log.TextLogItemData

/**
 * One `LazyColumn` key per row, same size and order as [rows]. Keys come from log ids, so a row keeps its key when
 * rows are added above it. A repeated key (e.g. the same day's header twice) gets `#2`, `#3`... because duplicate keys crash.
 */
fun logRowKeys(rows: List<LogItemData>): List<String> {
    val used = HashSet<String>(rows.size)
    return rows.map { row ->
        val base = baseKey(row)
        var key = base
        var n = 2
        while (!used.add(key)) key = "$base#${n++}"
        key
    }
}

private fun baseKey(row: LogItemData): String = when (row) {
    is TextLogItemData -> "t-${row.logId}"
    is HttpLogItemData -> "h-${row.logId.rawId}"
    is ImageLogItemData -> "i-${row.logId}"
    is SessionStartLogItemData -> "s-${row.detail.logId}"
    is ReplicatedTextLogItemData -> "g-${row.list.first().logId}"
    is RepeatedTextLogChildItemData -> "c-${row.log.logId}"
    is DateLogItemData -> "d-${row.presentDate.dumbed.hashCode()}"
    is NewLogsDividerItemData -> "new-divider"
}
