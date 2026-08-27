package com.khosravi.devin.present.client

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientDataExtTest {

    private fun clientWith(presenterConfig: JSONObject?) = ClientData("client.a", presenterConfig)

    private fun group(name: String, vararg tags: String) = JSONObject()
        .put("name", name)
        .put("tags", JSONArray(tags.toList()))

    private fun logNotifications(enabled: Boolean, groups: JSONArray? = null) = JSONObject().apply {
        put("enabled", enabled)
        if (groups != null) put("groups", groups)
    }

    private fun presenterConfig(logNotifications: JSONObject) = JSONObject()
        .put("logNotifications", logNotifications)

    @Test
    fun `valid groups are parsed`() {
        val config = clientWith(
            presenterConfig(
                logNotifications(
                    true,
                    JSONArray().put(group("OkHttp", "net", "http")).put(group("Crash", "crash")),
                )
            )
        ).getLogNotificationConfig()

        assertTrue(config.isEnabled)
        assertEquals(
            listOf(NotificationGroup("OkHttp", setOf("net", "http")), NotificationGroup("Crash", setOf("crash"))),
            config.groups,
        )
    }

    @Test
    fun `missing logNotifications key is disabled`() {
        val config = clientWith(JSONObject()).getLogNotificationConfig()
        assertFalse(config.isEnabled)
        assertTrue(config.groups.isEmpty())
    }

    @Test
    fun `null presenterConfig is disabled`() {
        val config = clientWith(null).getLogNotificationConfig()
        assertFalse(config.isEnabled)
    }

    @Test
    fun `enabled false is disabled regardless of groups`() {
        val config = clientWith(
            presenterConfig(logNotifications(false, JSONArray().put(group("OkHttp", "net"))))
        ).getLogNotificationConfig()
        assertFalse(config.isEnabled)
    }

    @Test
    fun `group missing name is skipped`() {
        val badGroup = JSONObject().put("tags", JSONArray().put("net"))
        val config = clientWith(
            presenterConfig(logNotifications(true, JSONArray().put(badGroup).put(group("Crash", "crash"))))
        ).getLogNotificationConfig()

        assertEquals(listOf(NotificationGroup("Crash", setOf("crash"))), config.groups)
    }

    @Test
    fun `non-string tag entries are skipped but group survives`() {
        val jsonGroup = JSONObject()
            .put("name", "Mixed")
            .put("tags", JSONArray().put("net").put(42).put("http"))
        val config = clientWith(
            presenterConfig(logNotifications(true, JSONArray().put(jsonGroup)))
        ).getLogNotificationConfig()

        assertEquals(listOf(NotificationGroup("Mixed", setOf("net", "http"))), config.groups)
    }

    @Test
    fun `group with zero valid tags is skipped`() {
        val jsonGroup = JSONObject().put("name", "Empty").put("tags", JSONArray().put(42))
        val config = clientWith(
            presenterConfig(logNotifications(true, JSONArray().put(jsonGroup).put(group("Crash", "crash"))))
        ).getLogNotificationConfig()

        assertEquals(listOf(NotificationGroup("Crash", setOf("crash"))), config.groups)
    }

    @Test
    fun `enabled true with no valid groups is disabled`() {
        val badGroup = JSONObject().put("tags", JSONArray().put("net"))
        val config = clientWith(
            presenterConfig(logNotifications(true, JSONArray().put(badGroup)))
        ).getLogNotificationConfig()

        assertFalse(config.isEnabled)
    }

    @Test
    fun `enabled true with missing groups array is disabled`() {
        val config = clientWith(presenterConfig(logNotifications(true))).getLogNotificationConfig()
        assertFalse(config.isEnabled)
    }

    @Test
    fun `groupFor returns first matching group when tag is in two groups`() {
        val config = LogNotificationConfig(
            true,
            listOf(NotificationGroup("First", setOf("shared")), NotificationGroup("Second", setOf("shared"))),
        )
        assertEquals(NotificationGroup("First", setOf("shared")), config.groupFor("shared"))
    }

    @Test
    fun `groupFor returns null when no group matches`() {
        val config = LogNotificationConfig(true, listOf(NotificationGroup("OkHttp", setOf("net"))))
        assertNull(config.groupFor("crash"))
    }

    @Test
    fun `groupFor returns null when config is disabled`() {
        val config = LogNotificationConfig(false, listOf(NotificationGroup("OkHttp", setOf("net"))))
        assertNull(config.groupFor("net"))
    }

    @Test
    fun `getLogPassword is unaffected by the group rewrite`() {
        val presenterConfig = JSONObject().put("logPassword", "1234")
        assertEquals("1234", clientWith(presenterConfig).getLogPassword())
    }
}
