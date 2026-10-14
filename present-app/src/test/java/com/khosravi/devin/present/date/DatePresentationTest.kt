package com.khosravi.devin.present.date

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class DatePresentationTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `time formats with zero padding`() {
        assertEquals("01:02:03", TimePresent.getFormatted(DumbTime(1, 2, 3)))
        assertEquals("23:59:59", TimePresent.getFormatted(DumbTime(23, 59, 59)))
    }

    @Test
    fun `date formats without padding`() {
        assertEquals("2026-1-5", DatePresent.getFormatted(DumbDate(2026, 1, 5)))
    }

    @Test
    fun `date time joins date and time with T`() {
        val present = DateTimePresent(0).apply {
            dumbed = DumbDateTime(DumbDate(2026, 10, 14), DumbTime(9, 5, 0))
        }
        assertEquals("2026-10-14T09:05:00", present.getFormatted())
    }

    @Test
    fun `initIfNeed fills time from timestamp in local time zone`() {
        val proxy = CalendarProxy(CalendarType.GREGORIAN)
        // 1970-01-01 01:02:03.456 UTC
        val time = proxy.initIfNeed(TimePresent(3_723_456L))
        assertEquals(DumbTime(1, 2, 3), time.dumbed)
        assertEquals("01:02:03", time.getFormatted())
    }

    @Test
    fun `initIfNeed keeps an existing value`() {
        val proxy = CalendarProxy(CalendarType.GREGORIAN)
        val existing = DumbTime(10, 0, 0)
        val time = TimePresent(0).apply { dumbed = existing }
        assertSame(existing, proxy.initIfNeed(time).dumbed)
    }

    @Test
    fun `getFormattedWithMillis appends zero-padded millis`() {
        val proxy = CalendarProxy(CalendarType.GREGORIAN)
        assertEquals("01:02:03.456", proxy.getFormattedWithMillis(TimePresent(3_723_456L)))
        assertEquals("00:00:01.007", proxy.getFormattedWithMillis(TimePresent(1_007L)))
    }
}
