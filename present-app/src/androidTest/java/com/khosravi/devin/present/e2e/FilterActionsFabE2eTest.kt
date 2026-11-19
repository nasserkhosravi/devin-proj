package com.khosravi.devin.present.e2e

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Presenter 4.11.0: the floating "Filter actions" button for the selected filter (inventory section 13). */
@RunWith(AndroidJUnit4::class)
class FilterActionsFabE2eTest : E2eTest() {


    private val allItems = listOf("Pin", "Unpin", "Share as json", "Select multiple", "Edit", "Remove", "Create custom filter")

    /** Opens the FAB menu and returns its item labels, bottom-most last (they stack upwards from the FAB). */
    private fun openFab(): List<String> {
        Ui.clickDesc("Filter actions")
        // Right after a collapse the slow device can drop the first tap; one retry keeps that from failing the test.
        if (!Waits.until(5_000) { Ui.exists(Ui.desc("Close filter actions")) }) Ui.clickDesc("Filter actions")
        Ui.waitForDesc("Close filter actions")
        Ui.waitForDesc("Create custom filter")
        return Ui.findAll { it.contentDescription?.toString() in allItems }.sortedBy { Ui.bounds(it).top }.map { it.contentDescription.toString() }
    }

    private fun closeFabWithBack() {
        Ui.back()
        Ui.waitForDesc("Filter actions")
        Ui.waitGone("fab items", predicate = Ui.desc("Create custom filter"))
    }

    /** LIST-40: with "All" selected the FAB offers only "Create custom filter"; back collapses it; it opens the dialog. */
    @Test
    fun all_onlyCreateFilter() {
        openLogList { TestData.log("A", "a value") }
        Ui.waitForText("a value")

        assertEquals(listOf("Create custom filter"), openFab())
        closeFabWithBack()
        Ui.waitForText("a value")

        openFab()
        Ui.clickDesc("Create custom filter")
        Ui.waitForText("Tags")
        Ui.clickText("confirm")
        Ui.waitForText("Pick at least one tag")
    }

    /**
     * LIST-40, LIST-41: a tag chip's FAB has Pin, Share as json, Create custom filter (no Select multiple, no Edit),
     * in that order; Pin pins the selected chip and the label turns to Unpin; Create preselects the tag.
     */
    @Test
    fun tagFilter_itemsPinAndCreateWithTag() {
        openLogList {
            TestData.log("Apple", "apple log", date = TestData.ts(30))
            TestData.log("Zed", "zed log", date = TestData.ts(20))
        }
        Ui.waitForText("zed log")
        val target = chipLabels().filter { it == "Apple" || it == "Zed" }.last()
        selectChip(target)
        Ui.waitForText("Contain text in log value")

        assertEquals(listOf("Pin", "Share as json", "Create custom filter"), openFab())
        Ui.clickDesc("Pin")
        Waits.require("$target pinned right after All (got ${chipLabels()})") { chipLabels().take(2) == listOf("All", target) }

        assertEquals(listOf("Unpin", "Share as json", "Create custom filter"), openFab())
        Ui.clickDesc("Create custom filter")
        Ui.waitForText("Tags")
        Ui.typeInto("title", "FromFab")
        // The selected tag is preselected, so confirming needs no tag pick.
        Ui.clickText("confirm")
        Ui.waitGone("dialog", predicate = Ui.text("confirm"))
        chip("FromFab")
    }

    /** LIST-41: "Share as json" from the FAB opens the share chooser with a JSON file, like the chip menu. */
    @Test
    fun tagFilter_shareAsJson() {
        IntentStubs.stubSystemUi()
        openLogList { TestData.log("A", "a value") }
        selectChip("A")
        Ui.waitForText("Contain text in log value")

        openFab()
        Ui.clickDesc("Share as json")

        val chooser = IntentStubs.waitForStarted("share chooser") { it.action == Intent.ACTION_CHOOSER }
        assertEquals("application/json", with(IntentStubs) { chooser.chooserTarget() }!!.type)
    }

    /** LIST-40, LIST-41: a custom filter's FAB has Pin, Edit, Remove, Create; Edit opens the filled dialog; Remove deletes it. */
    @Test
    fun customFilter_itemsEditAndRemove() {
        openLogList { TestData.log("A", "a value") }
        overflow("Create custom filter")
        pickFilterTag("A")
        Ui.typeInto("title", "Mine")
        Ui.clickText("confirm")
        Ui.waitGone("dialog", predicate = Ui.text("confirm"))
        selectChip("Mine")

        assertEquals(listOf("Pin", "Edit", "Remove", "Create custom filter"), openFab())
        Ui.clickDesc("Edit")
        Ui.waitForText("confirm")
        Waits.require("title prefilled") { with(Ui) { Ui.field("title").textValue() } == "Mine" }
        Ui.back()
        Ui.waitGone("dialog", predicate = Ui.text("confirm"))

        openFab()
        Ui.clickDesc("Remove")
        Ui.waitGone("chip Mine", predicate = Ui.text("Mine"))
        Ui.waitForText("a value")
    }

    /** LIST-41: tapping outside the open menu (the scrim) collapses it without running anything. */
    @Test
    fun scrimTap_collapses() {
        openLogList { TestData.log("A", "a value") }
        selectChip("A")
        openFab()
        Ui.tapAt(Ui.screenWidth() / 4, Ui.screenHeight() / 2)
        Ui.waitForDesc("Filter actions")
        Ui.waitGone("fab items", predicate = Ui.desc("Share as json"))
        // Nothing ran: the chip is still unpinned.
        assertEquals(listOf("Pin", "Share as json", "Create custom filter"), openFab())
    }

    /** LIST-42: the FAB is hidden in tag selection mode and comes back when selection ends. */
    @Test
    fun hiddenInSelectionMode() {
        openLogList {
            TestData.log("A", "a value", date = TestData.ts(30))
            TestData.log("B", "b value", date = TestData.ts(20))
        }
        Ui.waitForDesc("Filter actions")
        selectChips("A")
        Ui.waitGone("fab in selection mode", predicate = Ui.desc("Filter actions"))

        Ui.back()
        Ui.waitGone("selection bar", predicate = Ui.text("1 selected"))
        Ui.waitForDesc("Filter actions")
    }

    /** LIST-42: the FAB hides while scrolling down the list and shows again on scrolling up. */
    @Test
    fun hidesOnScrollDown_showsOnScrollUp() {
        openLogList { TestData.logs(60, "S") { i -> "scroll log $i" } }
        Ui.waitForText("scroll log 59")
        Ui.waitForDesc("Filter actions")
        val x = Ui.screenWidth() / 2
        val h = Ui.screenHeight()

        Ui.swipe(x, h * 3 / 4, x, h / 3)
        Ui.waitGone("fab after scrolling down", predicate = Ui.desc("Filter actions"))

        Ui.swipe(x, h / 2, x, h * 2 / 3)
        Ui.waitForDesc("Filter actions")
    }
}
