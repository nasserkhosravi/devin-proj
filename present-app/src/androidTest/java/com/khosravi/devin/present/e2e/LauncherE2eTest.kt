package com.khosravi.devin.present.e2e

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.e2e.Ui.selfOrAncestor
import com.khosravi.devin.present.e2e.Ui.textValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Presenter 4.11.0: the Launcher page (log list overflow › "Launcher"), inventory section 13. */
@RunWith(AndroidJUnit4::class)
class LauncherE2eTest : E2eTest() {

    private val view: (Intent) -> Boolean = { it.action == Intent.ACTION_VIEW }

    private fun openLauncher() {
        openLogList { TestData.log("A", "a value") }
        overflow("Launcher")
        Ui.waitForText("Launcher")
        Ui.waitForText("Deeplink")
    }

    /** The "Launch" button: the clickable parent of its label (disabled, it is neither clickable nor enabled). */
    private fun launchButton(): AccessibilityNodeInfo =
        Ui.waitFor("Launch button label") { it.textValue() == "Launch" && it.parent != null }.parent

    private fun launchEnabled(): Boolean = launchButton().let { it.isEnabled && it.isClickable }

    private fun fill(deeplink: String, key: String = "", value: String = "") {
        Ui.typeInto("Deeplink", deeplink)
        Ui.typeInto("Extra key", key)
        Ui.typeInto("Extra value (JSON object)", value)
    }

    private fun cardCount(deeplink: String) = Ui.findAll { it.textValue() == deeplink && !it.isEditable && it.selfOrAncestor { a -> a.isEditable } == null }.size

    private fun views() = IntentStubs.started().filter(view)

    /** LAUNCH-01: the overflow opens "Launcher" with the inputs, a disabled Launch and the empty saved-list text; back returns. */
    @Test
    fun open_emptyState() {
        openLauncher()
        Ui.waitForText("Extra key")
        Ui.waitForText("Extra value (JSON object)")
        Ui.waitForText("Launches you make are saved here.")
        assertFalse("Launch disabled without a deeplink", launchEnabled())

        Ui.clickDesc("Navigate up")
        awaitLogList()
        Ui.waitForText("a value")
    }

    /** LAUNCH-03: a deeplink needs a scheme, a value needs a key, and the value must be a JSON object; Launch stays disabled. */
    @Test
    fun validationErrors_disableLaunch() {
        openLauncher()

        fill("host/path")
        Ui.waitForText("Start with a scheme, like myapp://")
        assertFalse(launchEnabled())

        fill("myapp://home", key = "", value = "{\"a\":1}")
        Ui.waitGone("deeplink error", predicate = Ui.text("Start with a scheme, like myapp://"))
        Ui.waitForText("Add a key for this value, or clear the value")
        assertFalse(launchEnabled())

        fill("myapp://home", key = "payload", value = "[1]")
        Ui.waitForText("Must be a JSON object, like {\"key\": \"value\"}")
        assertFalse(launchEnabled())

        fill("myapp://home", key = "payload", value = "{\"a\":1}")
        Ui.waitGone("json error", predicate = Ui.textContains("Must be a JSON object"))
        Waits.require("Launch enabled when valid") { launchEnabled() }
    }

    /**
     * LAUNCH-02: Launch sends ACTION_VIEW for the deeplink to the client package only, with the JSON as a String extra,
     * and saves the launch at the top of the list; an identical launch (other JSON spacing) adds no duplicate; the
     * saved launch is kept after leaving the page and the key is prefilled next time.
     */
    @Test
    fun launch_firesIntentAndSaves() {
        IntentStubs.stubSystemUi()
        openLauncher()

        fill("myapp://home", key = "payload", value = "{\"a\":1}")
        Ui.click(launchButton())

        val intent = IntentStubs.waitForStarted("ACTION_VIEW to the client", matcher = view)
        assertEquals("myapp://home", intent.dataString)
        assertEquals(TestData.CLIENT, intent.`package`)
        assertEquals("{\"a\":1}", intent.getStringExtra("payload"))
        Ui.waitGone("empty text", predicate = Ui.text("Launches you make are saved here."))
        Waits.require("one saved card") { cardCount("myapp://home") == 1 }
        Ui.waitForText("payload")

        fill("myapp://home", key = "payload", value = "{ \"a\" : 1 }")
        Ui.click(launchButton())
        Waits.require("second launch sent") { views().size == 2 }
        Waits.holds("still one saved card", 1_000) { cardCount("myapp://home") == 1 }

        Ui.clickDesc("Navigate up")
        awaitLogList()
        overflow("Launcher")
        Waits.require("saved card kept") { cardCount("myapp://home") == 1 }
        Waits.require("key prefilled with the last key") { Ui.field("Extra key").textValue() == "payload" }
    }

    /** LAUNCH-04: Relaunch sends the saved launch again; Edit fills the inputs ("Editing…") and Launch updates that entry. */
    @Test
    fun relaunchAndEdit() {
        IntentStubs.stubSystemUi()
        openLauncher()
        fill("myapp://first", key = "k", value = "{\"n\":1}")
        Ui.click(launchButton())
        Waits.require("saved") { cardCount("myapp://first") == 1 }

        Ui.clickText("Relaunch")
        Waits.require("relaunch sent") { views().size == 2 }
        assertEquals("myapp://first", views().last().dataString)
        assertEquals("{\"n\":1}", views().last().getStringExtra("k"))

        Ui.clickText("Edit")
        Ui.waitForText("Editing…")
        Waits.require("deeplink filled in") { Ui.field("Deeplink").textValue() == "myapp://first" }
        Ui.typeInto("Deeplink", "myapp://second")
        Ui.click(launchButton())

        Waits.require("edit launched") { views().size == 3 }
        assertEquals("myapp://second", views().last().dataString)
        Waits.require("entry updated in place") { cardCount("myapp://second") == 1 && cardCount("myapp://first") == 0 }
        Ui.waitGone("editing chip", predicate = Ui.text("Editing…"))
    }

    /** LAUNCH-04: Edit then the "Stop editing" ✕ leaves editing mode without launching. */
    @Test
    fun cancelEdit() {
        IntentStubs.stubSystemUi()
        openLauncher()
        fill("myapp://first")
        Ui.click(launchButton())
        Waits.require("saved") { cardCount("myapp://first") == 1 }

        Ui.clickText("Edit")
        Ui.waitForText("Editing…")
        Ui.clickDesc("Stop editing")
        Ui.waitGone("editing chip", predicate = Ui.text("Editing…"))
        assertEquals(1, views().size)
    }

    /** LAUNCH-04: Delete removes the saved launch with a "Launch removed" snackbar whose Undo brings it back. */
    @Test
    fun deleteAndUndo() {
        IntentStubs.stubSystemUi()
        openLauncher()
        fill("myapp://doomed")
        Ui.click(launchButton())
        Waits.require("saved") { cardCount("myapp://doomed") == 1 }

        Ui.clickText("Delete")
        Ui.waitForText("Launch removed")
        Waits.require("card gone") { cardCount("myapp://doomed") == 0 }
        Ui.clickText("Undo")
        Waits.require("card restored") { cardCount("myapp://doomed") == 1 }
    }

    /** LAUNCH-05: a deeplink the client can't open shows "This app has no screen for that link" and saves nothing. */
    @Test
    fun noHandler_showsErrorAndSavesNothing() {
        openLauncher()
        fill("devine2enohandler://nothing")
        Ui.click(launchButton())

        Ui.waitForText("This app has no screen for that link")
        Ui.waitForText("Launches you make are saved here.")
        assertTrue("attempt recorded", views().isNotEmpty())
        assertEquals(0, cardCount("devine2enohandler://nothing"))
    }
}
