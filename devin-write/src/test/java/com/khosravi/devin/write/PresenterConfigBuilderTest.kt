package com.khosravi.devin.write

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenterConfigBuilderTest {

    private fun JSONObject.groups() = getJSONObject("logNotifications").getJSONArray("groups")

    private fun JSONObject.tags() = getJSONArray("tags").let { a -> List(a.length()) { a.getString(it) } }

    @Test
    fun `empty builder produces an empty object`() {
        assertEquals(0, DevinTool.PresenterConfigBuilder().build().length())
    }

    @Test
    fun `logPassword is written without notifications`() {
        val json = DevinTool.PresenterConfigBuilder().logPassword("secret").build()

        assertEquals("secret", json.getString("logPassword"))
        assertFalse(json.has("logNotifications"))
    }

    @Test
    fun `whitelist groups keep call order and are enabled`() {
        val json = DevinTool.PresenterConfigBuilder()
            .putNotificationWhitelistGroup("Errors", "crash", "http_5xx", color = "#FF0000")
            .putNotificationWhitelistGroup("Network", "dvn_okhttp")
            .build()

        assertTrue(json.getJSONObject("logNotifications").getBoolean("enabled"))
        val groups = json.groups()
        assertEquals(2, groups.length())
        assertEquals("Errors", groups.getJSONObject(0).getString("name"))
        assertEquals(listOf("crash", "http_5xx"), groups.getJSONObject(0).tags())
        assertEquals("#FF0000", groups.getJSONObject(0).getString("color"))
        assertEquals("Network", groups.getJSONObject(1).getString("name"))
        assertEquals(listOf("dvn_okhttp"), groups.getJSONObject(1).tags())
        assertFalse(groups.getJSONObject(1).has("color"))
    }

    @Test
    fun `uncategorized group is a wildcard placed after whitelist groups`() {
        val json = DevinTool.PresenterConfigBuilder()
            .setNotificationUncategorizedGroup("Others")
            .putNotificationWhitelistGroup("Errors", "crash")
            .build()

        val groups = json.groups()
        assertEquals(2, groups.length())
        assertEquals("Errors", groups.getJSONObject(0).getString("name"))
        assertEquals("Others", groups.getJSONObject(1).getString("name"))
        assertEquals(listOf("*"), groups.getJSONObject(1).tags())
    }

    @Test
    fun `setting the uncategorized group again replaces it`() {
        val json = DevinTool.PresenterConfigBuilder()
            .setNotificationUncategorizedGroup("First")
            .setNotificationUncategorizedGroup("Second", color = "#00FF00")
            .build()

        val groups = json.groups()
        assertEquals(1, groups.length())
        assertEquals("Second", groups.getJSONObject(0).getString("name"))
        assertEquals("#00FF00", groups.getJSONObject(0).getString("color"))
    }
}
