package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.DumbDate
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.SessionStartLogItemData
import com.khosravi.devin.present.log.TextLogItemData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRowKeysTest {

    private fun text(id: Long, value: String = "msg") = TextLogItemData("tag", value, TimePresent(id), 0, null, id)

    private fun header(day: Int) = DateLogItemData(DatePresent(0L, DumbDate(2026, 11, day)))

    @Test
    fun `keys come from log ids, not positions`() {
        assertEquals(listOf("t-5", "t-4"), logRowKeys(listOf(text(5), text(4))))

        val keys = logRowKeys(listOf(text(6), text(5), text(4)))
        assertEquals(listOf("t-5", "t-4"), keys.takeLast(2))
    }

    @Test
    fun `duplicate keys are made unique`() {
        val rows: List<LogItemData> = listOf(header(1), text(2), header(1))
        val keys = logRowKeys(rows)

        assertEquals(rows.size, keys.size)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(keys[0] + "#2", keys[2])
        assertTrue(keys[0].startsWith("d-"))
    }

    @Test
    fun `session start and group keys use the first log id`() {
        val session = SessionStartLogItemData(
            appVersionName = "1.0",
            datePresent = DatePresent(0L),
            timePresent = TimePresent(0L),
            detail = text(3, "session"),
        )
        val group = ReplicatedTextLogItemData(listOf(text(9, "a"), text(8, "a")))

        assertEquals(listOf("s-3", "g-9"), logRowKeys(listOf(session, group)))
    }
}
