package com.khosravi.devin.present.notification

import android.Manifest
import android.content.Intent
import androidx.activity.ComponentActivity
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.ClientLoadedState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LogNotificationLaunchCoordinatorTest {

    private lateinit var activity: ComponentActivity
    private lateinit var coordinator: LogNotificationLaunchCoordinator

    private val plain = ClientData("app.plain", null)
    private val notifying = ClientData(
        "app.notifying",
        JSONObject().put(
            "logNotifications",
            JSONObject().put("enabled", true)
                .put("groups", JSONArray().put(JSONObject().put("name", "All").put("tags", JSONArray().put("*")))),
        ),
    )

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        coordinator = LogNotificationLaunchCoordinator(activity) {}
    }

    private fun target(clientId: String?, tag: String? = null) = Intent().apply {
        clientId?.let { putExtra(LogNotificationLaunchCoordinator.EXTRA_TARGET_CLIENT_ID, it) }
        tag?.let { putExtra(LogNotificationLaunchCoordinator.EXTRA_TARGET_TAG, it) }
    }

    @Test
    fun `no target without extras`() {
        coordinator.readTarget(target(null))
        assertNull(coordinator.takeTarget(ClientLoadedState.Single(plain)))
    }

    @Test
    fun `finds the target client in a single-client state`() {
        coordinator.readTarget(target(plain.id, "net"))
        assertEquals(LogNotificationLaunchCoordinator.Target(plain, "net"), coordinator.takeTarget(ClientLoadedState.Single(plain)))
    }

    @Test
    fun `finds the target client among several clients`() {
        coordinator.readTarget(target(notifying.id))
        val target = coordinator.takeTarget(ClientLoadedState.Multi(listOf(plain, notifying)))
        assertEquals(notifying, target?.client)
        assertNull(target?.tag)
    }

    @Test
    fun `target is consumed once taken`() {
        coordinator.readTarget(target(plain.id, "net"))
        coordinator.takeTarget(ClientLoadedState.Single(plain))
        assertNull(coordinator.takeTarget(ClientLoadedState.Single(plain)))
    }

    @Test
    fun `unknown or missing client gives no target and still consumes it`() {
        coordinator.readTarget(target("app.gone"))
        assertNull(coordinator.takeTarget(ClientLoadedState.Single(plain)))

        coordinator.readTarget(target(plain.id))
        assertNull(coordinator.takeTarget(ClientLoadedState.Zero))
        assertNull(coordinator.takeTarget(ClientLoadedState.Single(plain)))
    }

    @Test
    fun `no permission request when no client enables notifications`() {
        assertFalse(coordinator.requestPermissionIfNeeded(ClientLoadedState.Multi(listOf(plain))))
        assertFalse(coordinator.requestPermissionIfNeeded(ClientLoadedState.Zero))
    }

    @Test
    fun `requests permission when a client enables notifications and it is not granted`() {
        shadowOf(activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(coordinator.requestPermissionIfNeeded(ClientLoadedState.Single(notifying)))
    }

    @Test
    fun `no permission request when already granted`() {
        shadowOf(activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(coordinator.requestPermissionIfNeeded(ClientLoadedState.Single(notifying)))
    }
}
