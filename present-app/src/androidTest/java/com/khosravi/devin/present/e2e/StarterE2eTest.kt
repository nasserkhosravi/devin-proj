package com.khosravi.devin.present.e2e

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.present.LogActivity
import com.khosravi.devin.present.e2e.Ui.textValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StarterE2eTest : E2eTest() {

    /** START-01, START-02: empty client table → title, refresh button and the zero-clients message, no rows. */
    @Test
    fun noClients_showsZeroStateMessage() {
        launchStarter()

        Ui.waitForText("Client selection")
        Ui.waitForDesc("Refresh")
        Ui.waitForText("No client data found, run client app or send logs to devin")
        Ui.assertNotShown("a client row") { it.textValue()?.startsWith("e2e.") == true }
    }

    /** START-03: exactly one client → "One client found" and the app opens its log list without a tap. */
    @Test
    fun oneClient_autoRoutesToLogList() {
        TestData.client()
        TestData.log("A", "hello")

        launchStarter()

        awaitLogList()
        Ui.waitForText("hello")
    }

    /** START-04, START-05: two clients → "Choose client source", one row per package id, no auto-route; tap opens. */
    @Test
    fun twoClients_listsBothWithoutAutoRoute_tapOpensLogs() {
        TestData.client(TestData.CLIENT)
        TestData.client(TestData.CLIENT_B)
        TestData.log("A", "log of b", clientId = TestData.CLIENT_B)

        launchStarter()

        Ui.waitForText("Choose client source")
        Ui.waitForText(TestData.CLIENT)
        Ui.waitForText(TestData.CLIENT_B)
        Waits.holds("stays on Starter", 1_000) { Ui.exists(Ui.text("Client selection")) }

        Ui.clickText(TestData.CLIENT_B)
        awaitLogList()
        Ui.waitForText("log of b")
    }

    /** START-07, LIST-31: back from the auto-routed log list shows Starter ("One client found" + row) without re-routing. */
    @Test
    fun backFromLogList_showsStarterAndDoesNotRouteAgain() {
        TestData.client()
        launchStarter()
        awaitLogList()

        Ui.back()

        Ui.waitForText("One client found")
        Ui.waitForText(TestData.CLIENT)
        Waits.require("log list destroyed") { AppState.activities().none { it is LogActivity } }
        Waits.holds("stays on Starter, no new log list", 2_000) { AppState.activities().none { it is LogActivity } }
        Ui.waitForText("Client selection")
    }

    /** START-06, START-09: refresh refetches; a second client shows both rows, and zero clients clear stale rows. */
    @Test
    fun refresh_refetchesClientList() {
        TestData.client(TestData.CLIENT)
        TestData.client(TestData.CLIENT_B)
        launchStarter()
        Ui.waitForText("Choose client source")

        AppState.context.contentResolver.delete(com.khosravi.devin.read.DevinUriHelper.getClientListUri(), null, null)
        Ui.clickDesc("Refresh")

        Ui.waitForText("No client data found, run client app or send logs to devin")
        Ui.assertNotShown("stale client rows") { it.textValue() == TestData.CLIENT || it.textValue() == TestData.CLIENT_B }
    }

    /** UPD-01: footer "v<versionName> by nasser.khosravi"; tapping it opens the GitHub releases page. */
    @Test
    fun footer_showsVersionAndOpensReleasesPage() {
        IntentStubs.stubSystemUi()
        launchStarter()

        val footer = "v${BuildConfig.VERSION_NAME} by nasser.khosravi"
        Ui.clickText(footer)

        val view = IntentStubs.waitForStarted("ACTION_VIEW of the releases page") { it.action == Intent.ACTION_VIEW }
        assertEquals("https://github.com/nasserkhosravi/devin-proj/releases", view.dataString)
        assertTrue(Ui.exists(Ui.text("Client selection")))
    }
}
