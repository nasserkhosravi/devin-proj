package com.khosravi.devin.present.e2e

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.present.LogActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Update prompts driven by a seeded "latest release" cache (no network); current version is [BuildConfig.VERSION_NAME]. */
@RunWith(AndroidJUnit4::class)
class UpdateDialogsE2eTest : E2eTest() {

    private val current = BuildConfig.VERSION_NAME
    private val optionalVersion = current.split('.').let { "${it[0]}.${it[1].toInt() + 1}.0" }
    private val forceVersion = "${current.split('.')[0].toInt() + 1}.0.0"
    private val releaseUrl = "https://github.com/nasserkhosravi/devin-proj/releases/tag/presenter_e2e"

    private fun logListOpen() = AppState.activities().any { it is LogActivity }

    /** UPD-02, UPD-03: optional update → red footer text and the "Devin update available" dialog with Download/Later. */
    @Test
    fun optionalUpdate_footerAndDialog() {
        AppState.seedLatestRelease(optionalVersion, releaseUrl)
        launchStarter()

        Ui.waitForText("Devin update available")
        Ui.waitForText("Version $optionalVersion is available (you have $current).")
        Ui.waitForText("Download")
        Ui.clickText("Later")
        Ui.waitGone("dialog", predicate = Ui.text("Devin update available"))
        Ui.waitForText("v$current by nasser.khosravi · Update available: v$optionalVersion")
    }

    /** UPD-04: with one client, routing waits behind the prompt; "Later" closes it and then opens the log list. */
    @Test
    fun optionalUpdate_holdsAutoRouteUntilLater() {
        AppState.seedLatestRelease(optionalVersion, releaseUrl)
        TestData.client()
        launchStarter()

        Ui.waitForText("Devin update available")
        Waits.holds("log list not opened while the prompt is up", 1_500) { !logListOpen() }
        Ui.clickText("Later")
        awaitLogList()
    }

    /** UPD-05: "Download" opens the release page and does NOT open the log list. */
    @Test
    fun optionalUpdate_downloadOpensPageWithoutRouting() {
        IntentStubs.stubSystemUi()
        AppState.seedLatestRelease(optionalVersion, releaseUrl)
        TestData.client()
        launchStarter()

        Ui.clickText("Download")

        val view = IntentStubs.waitForStarted("release page") { it.action == Intent.ACTION_VIEW }
        assertEquals(releaseUrl, view.dataString)
        Waits.holds("Download drops the held-back route", 2_000) { !logListOpen() }
        // Coming back from the browser re-shows the prompt (up to 3 a day); its route was dropped, so Later doesn't route.
        if (Ui.exists(Ui.text("Later"))) Ui.clickText("Later")
        Ui.waitForText("One client found")
        Waits.holds("still on Starter", 2_000) { !logListOpen() }
    }

    /**
     * UPD-06: a major update shows "Devin update required" with only "Download" (shown again after it), blocking routing;
     * back closes the app.
     */
    @Test
    fun forceUpdate_blocksRoutingAndBackCloses() {
        IntentStubs.stubSystemUi()
        AppState.seedLatestRelease(forceVersion, releaseUrl)
        TestData.client(TestData.CLIENT)
        TestData.client(TestData.CLIENT_B)
        launchStarter()

        Ui.waitForText("Devin update required")
        Ui.waitForText("Version $forceVersion is a major update and is required (you have $current).")
        Ui.waitForText("Download")
        Ui.assertNotShown("no Later on a force update", Ui.text("Later"))

        Ui.clickText("Download")
        val view = IntentStubs.waitForStarted("release page") { it.action == Intent.ACTION_VIEW }
        assertEquals(releaseUrl, view.dataString)
        Ui.waitForText("Devin update required")

        Ui.back()
        Waits.require("app closed") { AppState.activities().isEmpty() }
        assertTrue(!logListOpen())
    }
}
