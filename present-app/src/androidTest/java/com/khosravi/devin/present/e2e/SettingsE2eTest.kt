package com.khosravi.devin.present.e2e

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.e2e.Ui.selfOrAncestor
import com.khosravi.devin.present.e2e.Ui.textValue
import com.khosravi.devin.present.present.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsE2eTest : E2eTest() {

    private fun openSettings() {
        launch(SettingsActivity.intent(context))
        Ui.waitForText("Settings")
        Ui.waitForText("Storage")
    }

    private fun switchRow(): AccessibilityNodeInfo =
        Ui.waitForText("Auto-delete old logs").selfOrAncestor { it.isCheckable }!!

    private fun valueRow(title: String): AccessibilityNodeInfo =
        Ui.waitForText(title).selfOrAncestor { it.isClickable || !it.isEnabled && it.childCount > 0 }!!

    /** SET-01, SET-02, SET-03, SET-04, SET-06, SET-07: defaults on a fresh install. */
    @Test
    fun defaults() {
        openSettings()
        Ui.waitForDesc("Navigate up")
        Ui.waitForText("Keeps storage small by removing old logs automatically")
        assertTrue("auto-delete on by default", switchRow().isChecked)
        Ui.waitForText("Keep logs for")
        Ui.waitForText("7 days")
        Ui.waitForText("Max logs per app")
        Ui.waitForText("10,000")
        Ui.waitForText("Storage used")
        Ui.waitFor("storage size", predicate = Ui.textMatches(Regex("""\d.*B""")))
        Ui.waitForText("Pinned filter tags")
        Ui.waitForText("Pinned tags show first in the filter bar of every app that logs them")
        Ui.waitForText("okhttp")
        Ui.waitForDesc("Unpin okhttp")
        Ui.waitForText("Add tag")
    }

    /** SET-03, SET-04: pickers list the options, picking saves and closes, and the values survive a relaunch. */
    @Test
    fun retentionPickers_persist() {
        openSettings()
        Ui.clickText("7 days")
        listOf("1 day", "3 days", "7 days", "14 days", "30 days").forEach { Ui.waitForText(it) }
        Ui.clickText("30 days")
        Ui.waitGone("picker", predicate = Ui.text("14 days"))
        Ui.waitForText("30 days")

        Ui.clickText("10,000")
        listOf("1,000", "5,000", "10,000", "50,000").forEach { Ui.waitForText(it) }
        Ui.clickText("50,000")
        Ui.waitGone("picker", predicate = Ui.text("5,000"))

        AppState.finishAllActivities()
        openSettings()
        Ui.waitForText("30 days")
        Ui.waitForText("50,000")
    }

    /** SET-02, SET-05: switching auto-delete off persists and disables both value rows. */
    @Test
    fun switchOff_persistsAndDisablesRows() {
        openSettings()
        Ui.click(switchRow())
        Waits.require("switch off") { !switchRow().isChecked }
        Waits.require("rows disabled") { !valueRow("Keep logs for").isEnabled && !valueRow("Max logs per app").isEnabled }

        AppState.finishAllActivities()
        openSettings()
        assertFalse("still off after reopening", switchRow().isChecked)
    }

    /** SET-08, SET-07: "Add tag" pins a (trimmed) tag; the chip's close icon unpins it. */
    @Test
    fun addAndRemovePinnedTag() {
        openSettings()
        Ui.clickText("Add tag")
        Ui.waitForText("Tag, e.g. okhttp")
        Ui.setText(Ui.field("Tag, e.g. okhttp"), "  crash  ")
        Ui.clickText("OK")
        Ui.waitForDesc("Unpin crash")

        Ui.clickDesc("Unpin okhttp")
        Ui.waitGone("okhttp chip", predicate = Ui.desc("Unpin okhttp"))

        AppState.finishAllActivities()
        openSettings()
        Ui.waitForDesc("Unpin crash")
        Ui.assertNotShown("okhttp stays unpinned", Ui.desc("Unpin okhttp"))
    }

    /** SET-01: the back arrow closes Settings. */
    @Test
    fun backArrowCloses() {
        openSettings()
        Ui.clickDesc("Navigate up")
        Waits.require("settings closed") { AppState.activities().isEmpty() }
    }

    /** SET-11: with "1 day", a 3-day-old log is trimmed after the next insert. */
    @Test
    fun retention_trimsOldLogs() {
        openSettings()
        Ui.clickText("7 days")
        Ui.clickText("1 day")
        Ui.waitForText("1 day")
        AppState.finishAllActivities()

        TestData.client()
        TestData.log("A", "ancient log", date = TestData.daysAgo(3))
        TestData.log("A", "fresh log")
        Waits.require("old log trimmed in the background") { TestData.logCount() == 1L }

        launchStarter()
        awaitLogList()
        Ui.waitForText("fresh log")
        Ui.assertNotShown("trimmed log", Ui.text("ancient log"))
        assertEquals(1L, TestData.logCount())
        assertTrue(Ui.find(predicate = Ui.text("fresh log"))?.textValue() == "fresh log")
    }
}
