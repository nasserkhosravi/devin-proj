package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.data.LogId
import com.khosravi.devin.present.data.http.HttpLogData
import com.khosravi.devin.present.data.http.HttpLogOperationStatus
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.DumbDate
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.log.logIdOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NewLogsMergeTest {

    private fun text(id: Long) = TextLogItemData("tag", "msg $id", TimePresent(id), 0, null, id)

    private fun http(id: Long) = HttpLogItemData(
        HttpLogData(LogId(id), "name", "https://example.com/a", HttpLogOperationStatus.Requested, id, "GET")
    )

    private fun header(day: Int) = DateLogItemData(DatePresent(0L, DumbDate(2026, 11, day)))

    private fun ids(rows: List<LogItemData>) = rows.map { it.logIdOrNull() }

    @Test
    fun `newest id is the max id`() {
        assertEquals(9L, newestLogId(listOf(header(1), text(7), http(9), text(3))))
        assertNull(newestLogId(listOf(header(1))))
    }

    @Test
    fun `new rows go in front`() {
        val result = prependNewLogs(listOf(text(5)), listOf(text(7), text(6)))
        assertEquals(listOf<Long?>(7, 6, 5), ids(result))
    }

    @Test
    fun `same day header is not repeated`() {
        val oldHeader = header(1)
        val newHeader = header(1)
        val old5 = text(5)
        val new6 = text(6)

        val result = prependNewLogs(listOf(oldHeader, old5), listOf(newHeader, new6))

        assertEquals(3, result.size)
        assertSame(newHeader, result[0])
        assertSame(new6, result[1])
        assertSame(old5, result[2])
        assertEquals(1, result.count { it is DateLogItemData })
    }

    @Test
    fun `different day keeps both headers`() {
        val result = prependNewLogs(listOf(header(1), text(5)), listOf(header(2), text(6)))
        assertEquals(4, result.size)
        assertEquals(2, result.count { it is DateLogItemData })
    }

    @Test
    fun `rows already loaded are dropped`() {
        assertEquals(listOf<Long?>(5), ids(prependNewLogs(listOf(text(5)), listOf(text(5)))))

        val appended = appendDistinct(listOf(text(9), text(8)), listOf(text(8), text(7)))
        assertEquals(listOf<Long?>(9, 8, 7), ids(appended))
    }

    @Test
    fun `count skips headers`() {
        assertEquals(2, countLogRows(listOf(header(1), text(1), http(2))))
    }
}
