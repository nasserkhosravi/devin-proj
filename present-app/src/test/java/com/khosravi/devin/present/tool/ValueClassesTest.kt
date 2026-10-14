package com.khosravi.devin.present.tool

import org.junit.Assert.assertEquals
import org.junit.Test

class ValueClassesTest {

    @Test
    fun `NotEmptyString keeps its value`() {
        assertEquals(" ", NotEmptyString(" ").value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NotEmptyString rejects empty`() {
        NotEmptyString("")
    }

    @Test
    fun `PositiveNumber accepts zero and positives`() {
        assertEquals(0, PositiveNumber(0).value)
        assertEquals(30, PositiveNumber(30).value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `PositiveNumber rejects negatives`() {
        PositiveNumber(-1)
    }
}
