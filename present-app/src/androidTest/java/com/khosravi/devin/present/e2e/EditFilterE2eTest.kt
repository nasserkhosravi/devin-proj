package com.khosravi.devin.present.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.e2e.Ui.textValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Presenter 4.11.0: Edit on a custom filter chip (inventory section 13). */
@RunWith(AndroidJUnit4::class)
class EditFilterE2eTest : E2eTest() {

    private fun seed() {
        TestData.log("A", "alpha boom", date = TestData.ts(40))
        TestData.log("A", "alpha calm", date = TestData.ts(30))
        TestData.log("B", "beta boom", date = TestData.ts(20))
    }

    private fun createFilter(title: String, tag: String, search: String? = null) {
        overflow("Create custom filter")
        pickFilterTag(tag)
        Ui.typeInto("title", title)
        search?.let { Ui.typeInto("search value", it) }
        Ui.clickText("confirm")
        Ui.waitGone("dialog", predicate = Ui.text("confirm"))
        chip(title)
    }

    private fun fieldText(label: String): String? = Ui.field(label).textValue()

    /**
     * LIST-34: custom chip menu has Edit (tag chips don't); the dialog opens filled with the title and search text;
     * changing the search text and confirming keeps the chip in place, selects it and reloads its logs.
     */
    @Test
    fun edit_prefilledAndChangesApplyInPlace() {
        openLogList(::seed)
        Ui.waitForText("beta boom")
        createFilter("Ones", "A", search = "boom")
        awaitMessages(listOf("alpha boom"), absent = listOf("alpha calm", "beta boom"))
        createFilter("Other", "B")
        val before = chipLabels()

        chipMenu("A")
        Ui.waitForText("Pin")
        Ui.assertNotShown("Edit on a tag chip", Ui.text("Edit"))
        Ui.back()
        Ui.waitGone("tag chip menu", predicate = Ui.text("Share as json"))

        chipMenu("Ones")
        Ui.clickText("Edit")
        Ui.waitForText("confirm")
        Waits.require("title prefilled (got ${fieldText("title")})") { fieldText("title") == "Ones" }
        Waits.require("search prefilled (got ${fieldText("search value")})") { fieldText("search value") == "boom" }
        Ui.typeInto("search value", "calm")
        Ui.clickText("confirm")

        Ui.waitGone("dialog", predicate = Ui.text("confirm"))
        awaitMessages(listOf("alpha calm"), absent = listOf("alpha boom", "beta boom"))
        Waits.require("chip order unchanged (got ${chipLabels()}, was $before)") { chipLabels() == before }

        chipMenu("Ones")
        Ui.clickText("Edit")
        Waits.require("new search text saved (got ${fieldText("search value")})") { fieldText("search value") == "calm" }
    }

    /** Creates "One" (tag A) and pins it; with [relaunch], reopens the app so the list is reloaded from storage. */
    private fun pinnedOne(relaunch: Boolean) {
        openLogList(::seed)
        Ui.waitForText("beta boom")
        createFilter("One", "A")
        chipMenu("One")
        Ui.clickText("Pin")
        Waits.require("One pinned right after All (got ${chipLabels()})") { chipLabels().take(2) == listOf("All", "One") }
        if (relaunch) {
            relaunchApp()
            awaitLogList()
            Waits.require("pin survives a relaunch (got ${chipLabels()})") { chipLabels().take(2) == listOf("All", "One") }
        }
    }

    private fun renameOneToUno() {
        chipMenu("One")
        Ui.clickText("Edit")
        Ui.waitForText("confirm")
        Ui.typeInto("title", "Uno")
        Ui.clickText("confirm")
        Ui.waitGone("dialog", predicate = Ui.text("confirm"))
    }

    private fun assertUnoPinnedInOnesPlace() {
        Waits.require("Uno in One's place (got ${chipLabels()})") { chipLabels().take(2) == listOf("All", "Uno") }
        Ui.assertNotShown("old title", Ui.text("One"))
        chipMenu("Uno")
        Ui.waitForText("Unpin")
        Ui.back()
        Ui.waitGone("chip menu", predicate = Ui.text("Unpin"))
    }

    /** LIST-35: renaming a pinned filter keeps its place and its pin; the old title is gone, also after a relaunch. */
    @Test
    fun rename_keepsPinAndPosition() {
        pinnedOne(relaunch = true)
        renameOneToUno()
        assertUnoPinnedInOnesPlace()

        relaunchApp()
        awaitLogList()
        Waits.require("rename and pin survive a relaunch (got ${chipLabels()})") { chipLabels().take(2) == listOf("All", "Uno") }
        Ui.assertNotShown("old title after relaunch", Ui.text("One"))
    }

    /**
     * LIST-35, same as [rename_keepsPinAndPosition] but pinning and editing in one visit of the screen: Pin/Unpin
     * reorders only the screen's chips, so the edit must start from those, not from the view model's older list.
     */
    @Test
    fun rename_rightAfterPinning_keepsPinAndPosition() {
        pinnedOne(relaunch = false)
        renameOneToUno()
        assertUnoPinnedInOnesPlace()
    }

    /**
     * LIST-36: a title another filter uses is rejected with "A filter with this name already exists" and the dialog
     * stays open, both when editing and when creating; keeping the filter's own title is allowed.
     */
    @Test
    fun duplicateTitle_rejectedOnEditAndCreate() {
        openLogList(::seed)
        Ui.waitForText("beta boom")
        createFilter("One", "A")
        createFilter("Two", "B")

        chipMenu("Two")
        Ui.clickText("Edit")
        Ui.waitForText("confirm")
        Ui.typeInto("title", "One")
        Ui.clickText("confirm")
        Ui.waitForText("A filter with this name already exists")
        Waits.holds("edit dialog stays open", 1_000) { Ui.exists(Ui.text("confirm")) }
        Ui.typeInto("title", "Two")
        Ui.waitGone("error cleared by typing", predicate = Ui.text("A filter with this name already exists"))
        Ui.clickText("confirm")
        Ui.waitGone("dialog closes when the own title is kept", predicate = Ui.text("confirm"))

        overflow("Create custom filter")
        pickFilterTag("A")
        Ui.typeInto("title", "Two")
        Ui.clickText("confirm")
        Ui.waitForText("A filter with this name already exists")
        Waits.holds("create dialog stays open", 1_000) { Ui.exists(Ui.text("confirm")) }
        Ui.back()
        Ui.waitGone("dialog", predicate = Ui.text("confirm"))

        assertEquals("one chip each (got ${chipLabels()})", 1, chipLabels().count { it == "One" })
        assertEquals(1, chipLabels().count { it == "Two" })
    }
}
