package com.khosravi.devin.present.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.present.StarterActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The log notification's tap intent opens Starter with the target client id and tag extras
 * (`LatestLogNotificationObserver` builds it with [StarterActivity.EXTRA_TARGET_CLIENT_ID] / [StarterActivity.EXTRA_TARGET_TAG]).
 * The tests launch Starter with those extras directly instead of tapping the system notification shade.
 */
@RunWith(AndroidJUnit4::class)
class NotificationLaunchE2eTest : E2eTest() {

    private fun launchFromNotification(clientId: String, tag: String) = launchStarter {
        putExtra(StarterActivity.EXTRA_TARGET_CLIENT_ID, clientId)
        putExtra(StarterActivity.EXTRA_TARGET_TAG, tag)
    }

    /** NOTIF-02: with several clients, the target client's log list opens with the tag chip selected and filtered. */
    @Test
    fun multiClient_opensTargetClientWithTagSelected() {
        TestData.client(TestData.CLIENT, TestData.notificationConfig("Net", "A"))
        TestData.client(TestData.CLIENT_B)
        TestData.log("A", "a from target", date = TestData.ts(30))
        TestData.log("B", "b from target", date = TestData.ts(20))
        TestData.log("A", "a from other client", clientId = TestData.CLIENT_B)

        launchFromNotification(TestData.CLIENT, "A")

        awaitLogList()
        // A selected tag chip shows the search row and only that tag's rows of the target client.
        Ui.waitForText("Contain text in log value")
        awaitMessages(listOf("a from target"), absent = listOf("b from target", "a from other client"))
    }

    /** NOTIF-02: a tag that has no chip yet gets one appended and selected. */
    @Test
    fun tagWithoutChip_isAppendedAndSelected() {
        TestData.client(TestData.CLIENT, TestData.notificationConfig("Net", "Late"))
        TestData.log("Other", "other log")

        launchFromNotification(TestData.CLIENT, "Late")

        awaitLogList()
        chip("Late")
        Ui.waitForText("Contain text in log value")
        Ui.waitGone("other tag's rows filtered out", predicate = Ui.text("other log"))
    }

    /** NOTIF-03: a missing target client behaves like a normal launch. */
    @Test
    fun missingClient_normalLaunch() {
        TestData.client(TestData.CLIENT)
        TestData.client(TestData.CLIENT_B)

        launchFromNotification("gone.client", "A")

        Ui.waitForText("Choose client source")
        Waits.holds("no routing", 1_500) { Ui.exists(Ui.text("Client selection")) }
    }
}
