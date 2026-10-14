package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.TextLogItemData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingReplicatedTextLogItemDataOperationTest {

    private var nextTimestamp = 0L

    private fun text(value: String) = TextLogItemData("tag", value, TimePresent(nextTimestamp++), 0, null)

    private fun header() = DateLogItemData(DatePresent(nextTimestamp++))

    private fun run(vararg logs: LogItemData) = CountingReplicatedTextLogItemDataOperation(logs.toList()).get()

    @Test
    fun `empty input gives empty output`() {
        assertTrue(run().isEmpty())
    }

    @Test
    fun `single text log is kept as is`() {
        val a = text("a")
        val result = run(a)
        assertEquals(1, result.size)
        assertSame(a, result[0])
    }

    @Test
    fun `distinct consecutive texts are kept separately`() {
        val a = text("a")
        val b = text("b")
        assertEquals(listOf(a, b), run(a, b))
    }

    @Test
    fun `consecutive identical texts are merged in order`() {
        val first = text("same")
        val second = text("same")
        val third = text("same")
        val result = run(first, second, third)

        assertEquals(1, result.size)
        val replicated = result[0] as ReplicatedTextLogItemData
        assertEquals(listOf(first, second, third), replicated.list)
        assertEquals("same", replicated.text)
        assertSame(first.timePresent, replicated.timePresent)
    }

    @Test
    fun `identical texts separated by a different text are not merged`() {
        val a1 = text("a")
        val b = text("b")
        val a2 = text("a")
        assertEquals(listOf(a1, b, a2), run(a1, b, a2))
    }

    @Test
    fun `header breaks a run and is kept in place`() {
        val h1 = header()
        val a1 = text("a")
        val a2 = text("a")
        val h2 = header()
        val a3 = text("a")
        val result = run(h1, a1, a2, h2, a3)

        assertEquals(4, result.size)
        assertSame(h1, result[0])
        assertEquals(listOf(a1, a2), (result[1] as ReplicatedTextLogItemData).list)
        assertSame(h2, result[2])
        assertSame(a3, result[3])
    }

    @Test
    fun `merges a run directly followed by a different run`() {
        val a1 = text("a")
        val a2 = text("a")
        val b1 = text("b")
        val b2 = text("b")
        val result = run(a1, a2, b1, b2)

        assertEquals(2, result.size)
        assertEquals(listOf(a1, a2), (result[0] as ReplicatedTextLogItemData).list)
        assertEquals(listOf(b1, b2), (result[1] as ReplicatedTextLogItemData).list)
    }
}
