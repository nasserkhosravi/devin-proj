package com.khosravi.devin.present.log

import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.TimePresent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogItemDataIdsTest {

    private fun text(id: Long, time: Long = id, tag: String = "t", text: String = "m", level: Int = 4) =
        TextLogItemData(tag, text, TimePresent(time), level, null, id)

    @Test
    fun `text log returns its id`() {
        assertEquals(7L, text(7).logIdOrNull())
    }

    @Test
    fun `session start returns the id of its detail log`() {
        val item = SessionStartLogItemData("1.0", DatePresent(1), TimePresent(1), text(9))

        assertEquals(9L, item.logIdOrNull())
    }

    @Test
    fun `replicated group returns the id of its newest copy`() {
        assertEquals(30L, ReplicatedTextLogItemData(listOf(text(30), text(20), text(10))).logIdOrNull())
    }

    @Test
    fun `repeated child returns its own log id`() {
        assertEquals(20L, RepeatedTextLogChildItemData("key", text(20)).logIdOrNull())
    }

    @Test
    fun `rows that are not a stored log return null`() {
        assertNull(DateLogItemData(DatePresent(1)).logIdOrNull())
        assertNull(NewLogsDividerItemData(3).logIdOrNull())
    }

    @Test
    fun `replicated group exposes newest copy data, max level and count`() {
        val group = ReplicatedTextLogItemData(listOf(text(3, level = 4), text(2, level = 6), text(1, level = 5)))

        assertEquals(3, group.count)
        assertEquals(6, group.logLevel)
        assertEquals(3L, group.timePresent.timestamp)
        assertEquals("t", group.tag)
        assertEquals("m", group.text)
    }

    @Test
    fun `group key is stable for the same newest copy and differs otherwise`() {
        val a = ReplicatedTextLogItemData(listOf(text(3), text(2)))
        val sameNewest = ReplicatedTextLogItemData(listOf(text(3), text(2), text(1)))
        val newer = ReplicatedTextLogItemData(listOf(text(4), text(3), text(2)))
        val otherText = ReplicatedTextLogItemData(listOf(text(3, text = "x")))

        assertEquals(a.groupKey, sameNewest.groupKey)
        assert(a.groupKey != newer.groupKey)
        assert(a.groupKey != otherText.groupKey)
    }
}
