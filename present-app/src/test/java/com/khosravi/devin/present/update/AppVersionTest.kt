package com.khosravi.devin.present.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses plain version`() {
        assertEquals(AppVersion(4, 5, 0), AppVersion.parse("4.5.0"))
    }

    @Test
    fun `parses v-prefixed version`() {
        assertEquals(AppVersion(4, 5, 0), AppVersion.parse("v4.5.0"))
    }

    @Test
    fun `parses presenter tag shapes case-insensitively`() {
        assertEquals(AppVersion(4, 4, 0), AppVersion.parse("presenter_4.4.0"))
        assertEquals(AppVersion(4, 1, 0), AppVersion.parse("Presenter/4.1.0"))
        assertEquals(AppVersion(4, 1, 0), AppVersion.parse("presenter-4.1.0"))
        assertEquals(AppVersion(4, 1, 0), AppVersion.parse("presenter4.1.0"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("  1.2.3\n"))
    }

    @Test
    fun `rejects library tags and malformed versions`() {
        assertNull(AppVersion.parse("write_4.4.0"))
        assertNull(AppVersion.parse("write-okhttp_1.0.2"))
        assertNull(AppVersion.parse("4.5"))
        assertNull(AppVersion.parse("4.5.0.1"))
        assertNull(AppVersion.parse("4.5.0-SNAPSHOT"))
        assertNull(AppVersion.parse(""))
    }

    @Test
    fun `rejects numbers that overflow Int`() {
        assertNull(AppVersion.parse("99999999999.0.0"))
    }

    @Test
    fun `compares by major then minor then patch`() {
        assertTrue(AppVersion(5, 0, 0) > AppVersion(4, 9, 9))
        assertTrue(AppVersion(4, 6, 0) > AppVersion(4, 5, 9))
        assertTrue(AppVersion(4, 5, 1) > AppVersion(4, 5, 0))
        assertEquals(0, AppVersion(4, 5, 0).compareTo(AppVersion(4, 5, 0)))
    }

    @Test
    fun `numeric comparison, not lexical`() {
        assertTrue(AppVersion(4, 10, 0) > AppVersion(4, 9, 0))
    }

    @Test
    fun `toString is dotted version`() {
        assertEquals("4.5.0", AppVersion(4, 5, 0).toString())
    }
}
