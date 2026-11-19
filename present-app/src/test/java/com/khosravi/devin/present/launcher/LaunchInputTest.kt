package com.khosravi.devin.present.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchInputTest {

    @Test
    fun `blank deeplink is missing`() {
        assertEquals(LaunchInputError.MissingDeeplink, validateLaunchInput("  ", "", ""))
    }

    @Test
    fun `deeplink needs a scheme`() {
        assertEquals(LaunchInputError.InvalidDeeplink, validateLaunchInput("host/path", "", ""))
        assertNull(validateLaunchInput("myapp://x", "", ""))
        assertNull(validateLaunchInput("https://a.io", "", ""))
        assertNull(validateLaunchInput("tel:123", "", ""))
    }

    @Test
    fun `a value needs a key, a key alone is ignored`() {
        assertEquals(LaunchInputError.MissingKey, validateLaunchInput("a://b", "", "{}"))
        assertNull(validateLaunchInput("a://b", "k", ""))
        assertEquals(LaunchInput("a://b", null, null), LaunchInput.of("a://b", "k", " "))
        assertNull(validateLaunchInput("a://b", "", ""))
    }

    @Test
    fun `value must be a JSON object`() {
        assertEquals(LaunchInputError.InvalidJson, validateLaunchInput("a://b", "k", "[1]"))
        assertEquals(LaunchInputError.InvalidJson, validateLaunchInput("a://b", "k", "5"))
        assertEquals(LaunchInputError.InvalidJson, validateLaunchInput("a://b", "k", "{bad"))
        assertNull(validateLaunchInput("a://b", "k", "{\"a\":1}"))
    }

    @Test
    fun `of trims and turns blanks into null`() {
        val input = LaunchInput.of("  a://b ", "  ", " ")
        assertEquals(LaunchInput("a://b", null, null), input)
    }

    @Test
    fun `identity ignores JSON whitespace`() {
        val spaced = LaunchInput.of("a://b", "k", "{ \"a\" : 1,\n \"b\": {\"c\": true} }")
        val compact = LaunchInput.of("a://b", "k", "{\"a\":1,\"b\":{\"c\":true}}")
        assertEquals(compact.identity(), spaced.identity())
        assertNotEquals(compact.identity(), LaunchInput.of("a://b", "other", "{\"a\":1,\"b\":{\"c\":true}}").identity())
    }
}
