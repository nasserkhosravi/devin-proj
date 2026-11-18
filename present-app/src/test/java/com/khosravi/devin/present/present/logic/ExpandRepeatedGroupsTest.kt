package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.log.RepeatedTextLogChildItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.TextLogItemData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ExpandRepeatedGroupsTest {

    private var nextTimestamp = 100L

    private fun text(value: String) = TextLogItemData("tag", value, TimePresent(nextTimestamp--), 0, null)

    private fun group() = ReplicatedTextLogItemData(listOf(text("a"), text("a"), text("a")))

    @Test
    fun `collapsed group adds no children`() {
        val group = group()
        val result = listOf(group).expandRepeatedGroups(emptySet())
        assertEquals(listOf<Any>(group), result)
    }

    @Test
    fun `expanded group is followed by its copies in order`() {
        val group = group()
        val result = listOf(group).expandRepeatedGroups(setOf(group.groupKey))

        assertEquals(4, result.size)
        assertSame(group, result[0])
        group.list.forEachIndexed { i, copy ->
            val child = result[i + 1] as RepeatedTextLogChildItemData
            assertSame(copy, child.log)
            assertEquals(group.groupKey, child.groupKey)
        }
    }

    @Test
    fun `unknown expanded key adds nothing`() {
        val group = group()
        val plain = text("b")
        val result = listOf(group, plain).expandRepeatedGroups(setOf("missing"))
        assertEquals(listOf(group, plain), result)
    }
}
