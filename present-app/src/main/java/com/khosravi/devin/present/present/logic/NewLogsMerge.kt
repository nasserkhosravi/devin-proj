package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.logIdOrNull

/** The highest log id among [rows], or null when none of them is a log. */
fun newestLogId(rows: List<LogItemData>): Long? = rows.mapNotNull { it.logIdOrNull() }.maxOrNull()

/**
 * Puts [new] in front of [old], skipping rows already in [old]. When both start with a header for the same day,
 * the older header goes, so the day isn't announced twice.
 */
fun prependNewLogs(old: List<LogItemData>, new: List<LogItemData>): List<LogItemData> {
    val known = old.mapNotNullTo(HashSet()) { it.logIdOrNull() }
    val fresh = new.filter { it.logIdOrNull()?.let { id -> id !in known } ?: true }
    val firstOld = old.firstOrNull()
    val sameDay = firstOld is DateLogItemData && fresh.any {
        it is DateLogItemData && it.presentDate.dumbed == firstOld.presentDate.dumbed
    }
    return fresh + if (sameDay) old.drop(1) else old
}

/** Adds the next [page] after [old], skipping rows already in [old] (new logs shift the pages); headers stay. */
fun appendDistinct(old: List<LogItemData>, page: List<LogItemData>): List<LogItemData> {
    val known = old.mapNotNullTo(HashSet()) { it.logIdOrNull() }
    return old + page.filter { it.logIdOrNull()?.let { id -> id !in known } ?: true }
}

/** How many of [rows] are logs (headers don't count). */
fun countLogRows(rows: List<LogItemData>): Int = rows.count { it.logIdOrNull() != null }
