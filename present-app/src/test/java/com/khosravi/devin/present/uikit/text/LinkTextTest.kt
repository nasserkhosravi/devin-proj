package com.khosravi.devin.present.uikit.text

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTextTest {

    private val text = linkifiedString("see https://a.io/x now", Color.Blue)

    @Test
    fun `linkAt returns the uri inside a link`() {
        assertEquals("https://a.io/x", text.linkAt(4))
        assertEquals("https://a.io/x", text.linkAt(17))
    }

    @Test
    fun `linkAt returns null off a link`() {
        assertNull(text.linkAt(0))
        assertNull(text.linkAt(3))
    }

    @Test
    fun `linkAt treats the range end as outside`() {
        assertNull(text.linkAt(18))
    }

    @Test
    fun `www links resolve to https`() {
        assertEquals("https://www.a.io", linkifiedString("www.a.io", Color.Blue).linkAt(0))
    }

    @Test
    fun `needsCopyConfirmation is false from Android 13`() {
        assertTrue(needsCopyConfirmation(32))
        assertFalse(needsCopyConfirmation(33))
        assertFalse(needsCopyConfirmation(34))
    }
}
