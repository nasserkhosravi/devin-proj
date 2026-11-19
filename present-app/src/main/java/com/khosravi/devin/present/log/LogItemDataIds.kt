package com.khosravi.devin.present.log

/** The stored log's id, or null for rows that aren't one log (date headers, the new-logs divider). */
fun LogItemData.logIdOrNull(): Long? = when (this) {
    is TextLogItemData -> logId
    is HttpLogItemData -> logId.rawId
    is ImageLogItemData -> logId
    is SessionStartLogItemData -> detail.logId
    is ReplicatedTextLogItemData -> list.first().logId
    is RepeatedTextLogChildItemData -> log.logId
    is DateLogItemData -> null
    is NewLogsDividerItemData -> null
}
