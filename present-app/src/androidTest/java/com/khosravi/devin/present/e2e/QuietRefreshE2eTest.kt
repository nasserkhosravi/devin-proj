package com.khosravi.devin.present.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.e2e.Ui.textValue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Presenter 4.11.0: logs written while the user was away are merged in quietly on return (inventory section 13). */
@RunWith(AndroidJUnit4::class)
class QuietRefreshE2eTest : E2eTest() {

    private val pill = Ui.textMatches(Regex("↑ \\d+ new logs?"))

    /**
     * LIST-37: back at the top after Home + launcher, the new logs are at the top with a "2 new logs above" divider
     * below them; no "refreshed" toast, no spinner-driven reload, no pill.
     */
    @Test
    fun returnAtTop_mergesNewLogsWithDivider() {
        openLogList { TestData.log("A", "old log", date = TestData.ts(60)) }
        Ui.waitForText("old log")

        leaveAppAndReturn {
            TestData.log("A", "new log one", date = TestData.ts(20))
            TestData.log("A", "new log two", date = TestData.ts(10))
        }

        awaitLogList()
        awaitMessages(listOf("new log one", "new log two", "old log"))
        val divider = Ui.waitForText("2 new logs above")
        val tops = listOf("new log two", "new log one").map { Ui.bounds(Ui.waitForText(it)).top }
        assertTrue("new rows above the divider, newest first", tops[0] < tops[1] && tops[1] < Ui.bounds(divider).top)
        assertTrue("old row below the divider", Ui.bounds(Ui.waitForText("old log")).top > Ui.bounds(divider).top)
        Toasts.assertNone("refreshed")
        Ui.assertNotShown("pill at the top", pill)
    }

    /**
     * LIST-38: away while scrolled down: the row being read stays on screen and a "↑ 3 new logs" pill shows; tapping it
     * scrolls to the top (new logs and the divider) and the pill goes away.
     */
    @Test
    fun returnMidList_showsPillAndKeepsPosition() {
        openLogList { TestData.logs(60, "A") { i -> "row $i" } }
        Ui.waitForText("row 59")
        val list = Ui.find { it.isScrollable && Ui.bounds(it).height() > 600 }!!
        repeat(2) { Ui.scrollForward(list); Waits.appDelay(300) }
        Ui.waitGone("top row scrolled away", predicate = Ui.text("row 59"))
        val reading = Ui.findAll { it.textValue()?.matches(Regex("row \\d+")) == true }.map { it.textValue()!! }
        assertTrue("some rows visible", reading.isNotEmpty())

        leaveAppAndReturn {
            (1..3).forEach { TestData.log("A", "fresh $it", date = TestData.ts(10L - it)) }
        }

        awaitLogList()
        Ui.waitForText("↑ 3 new logs")
        Waits.require("the rows being read are still visible") { reading.any { Ui.exists(Ui.text(it)) } }
        Ui.assertNotShown("new rows not scrolled into view", Ui.text("fresh 3"))
        Toasts.assertNone("refreshed")

        Ui.clickText("↑ 3 new logs")
        Ui.waitForText("fresh 3")
        Ui.waitForText("3 new logs above")
        Ui.waitGone("pill at the top", predicate = pill)
    }

    /** LIST-39: with a tag chip selected, only that tag's new logs are merged and counted; the chips don't change. */
    @Test
    fun returnWithTagSelected_mergesOnlyMatchingLogs() {
        openLogList {
            TestData.log("A", "a old", date = TestData.ts(60))
            TestData.log("B", "b old", date = TestData.ts(50))
        }
        selectChip("A")
        awaitMessages(listOf("a old"), absent = listOf("b old"))
        val chipsBefore = chipLabels()

        leaveAppAndReturn {
            TestData.log("A", "a new", date = TestData.ts(20))
            TestData.log("B", "b new", date = TestData.ts(10))
            TestData.log("C", "c new", date = TestData.ts(5))
        }

        awaitLogList()
        Ui.waitForText("a new")
        Ui.waitForText("1 new log above")
        Waits.holds("other tags' logs not merged", 1_000) { !Ui.exists(Ui.text("b new")) && !Ui.exists(Ui.text("b old")) }
        Ui.waitForText("Contain text in log value")
        assertTrue("chips unchanged (got ${chipLabels()}, was $chipsBefore)", chipLabels() == chipsBefore)
    }

    /** LIST-37: coming back from Settings (another activity of the app) also merges new logs quietly. */
    @Test
    fun returnFromSettings_mergesNewLogs() {
        openLogList { TestData.log("A", "before settings", date = TestData.ts(60)) }
        Ui.waitForText("before settings")

        overflow("Settings")
        Ui.waitForText("Storage")
        TestData.log("A", "written during settings", date = TestData.ts(10))
        Ui.back()

        awaitLogList()
        Ui.waitForText("written during settings")
        Ui.waitForText("1 new log above")
        Toasts.assertNone("refreshed")
    }

    /** LIST-37: a refresh replaces the list, so the divider (and any pill) goes away. */
    @Test
    fun refreshClearsDivider() {
        openLogList { TestData.log("A", "old log", date = TestData.ts(60)) }
        Ui.waitForText("old log")
        leaveAppAndReturn { TestData.log("A", "new log", date = TestData.ts(10)) }
        awaitLogList()
        Ui.waitForText("1 new log above")

        Ui.clickDesc("Refresh")
        Toasts.waitFor("refreshed")
        Ui.waitGone("divider after refresh", predicate = Ui.text("1 new log above"))
        awaitMessages(listOf("new log", "old log"))
    }
}
