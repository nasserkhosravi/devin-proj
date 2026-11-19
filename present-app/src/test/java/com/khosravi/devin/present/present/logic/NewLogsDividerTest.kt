package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.NewLogsDividerItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.SessionStartLogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.present.groupRepeatedAndExpand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NewLogsDividerTest {

    private fun text(id: Long, value: String = "msg $id") = TextLogItemData("tag", value, TimePresent(id), 0, null, id)

    private fun session(id: Long) = SessionStartLogItemData(
        appVersionName = "1.0",
        datePresent = DatePresent(id),
        timePresent = TimePresent(id),
        detail = text(id, "session"),
    )

    @Test
    fun `divider goes before the first old row`() {
        val rows = listOf(text(7), text(6), text(5))
        val result = rows.withNewLogsDivider(5)

        assertEquals(4, result.size)
        assertSame(rows[0], result[0])
        assertSame(rows[1], result[1])
        assertEquals(2, (result[2] as NewLogsDividerItemData).count)
        assertSame(rows[2], result[3])
    }

    @Test
    fun `session start above new rows stays above the divider`() {
        val session = session(3)
        val new7 = text(7)
        val old5 = text(5)
        val result = listOf<LogItemData>(session, new7, old5).withNewLogsDivider(5)

        assertEquals(4, result.size)
        assertSame(session, result[0])
        assertSame(new7, result[1])
        assertEquals(1, (result[2] as NewLogsDividerItemData).count)
        assertSame(old5, result[3])
    }

    @Test
    fun `no divider when nothing is newer`() {
        val rows = listOf(text(7))
        assertSame(rows, rows.withNewLogsDivider(9))
    }

    @Test
    fun `null boundary adds nothing`() {
        val rows = listOf(text(7), text(6))
        assertSame(rows, rows.withNewLogsDivider(null))
    }

    @Test
    fun `groups don't merge across the divider`() {
        val rows = listOf(text(7, "same"), text(6, "same"))
        val result = rows.withNewLogsDivider(6).groupRepeatedAndExpand(emptySet())

        assertEquals(3, result.size)
        assertSame(rows[0], result[0])
        assertTrue(result[1] is NewLogsDividerItemData)
        assertSame(rows[1], result[2])
        assertTrue(result.none { it is ReplicatedTextLogItemData })
    }
}
