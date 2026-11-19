package com.khosravi.devin.present.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khosravi.devin.present.e2e.Ui.selfOrAncestor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Presenter 4.11.0: repeated text logs fold into one expandable row (inventory section 13). */
@RunWith(AndroidJUnit4::class)
class RepeatedLogsE2eTest : E2eTest() {

    private val oldest = TestData.ts(50)
    private val middle = TestData.ts(40)
    private val newest = TestData.ts(30)

    private fun seedRun() {
        TestData.log("Rep", "same message", date = oldest)
        TestData.log("Rep", "same message", date = middle)
        TestData.log("Rep", "same message", date = newest)
        TestData.log("Other", "other message", date = TestData.ts(20))
    }

    private fun messageCount() = Ui.count(Ui.text("same message"))

    /**
     * ROW-15, ROW-16: three back-to-back copies show as one row with the newest copy's time, a "×3" pill
     * ("Repeated 3 times") and "Expand"; expanding lists each copy with its own time; tapping a copy opens its
     * detail and back keeps the group expanded; "Collapse" folds it again.
     */
    @Test
    fun repeatedLogs_groupExpandOpenCollapse() {
        openLogList(::seedRun)
        Ui.waitForText("other message")

        Ui.waitForDesc("Repeated 3 times")
        Ui.waitForText("×3")
        Ui.waitForText(TestData.timeWithMillis(newest))
        Waits.require("one row for the three copies (got ${messageCount()})") { messageCount() == 1 }
        Ui.assertNotShown("older copy's time while collapsed", Ui.text(TestData.timeWithMillis(oldest)))

        Ui.clickDesc("Expand")
        Ui.waitForDesc("Collapse")
        Waits.require("header + 3 copies (got ${messageCount()})") { messageCount() == 4 }
        Ui.waitForText(TestData.timeWithMillis(oldest))
        Ui.waitForText(TestData.timeWithMillis(middle))

        Ui.clickText(TestData.timeWithMillis(oldest))
        Ui.waitForText("Log detail")
        Ui.waitForText("Rep")
        Ui.waitForText(TestData.timeWithMillis(oldest))
        Ui.back()

        awaitLogList()
        Ui.waitForDesc("Collapse")
        Waits.require("still expanded after back (got ${messageCount()})") { messageCount() == 4 }

        Ui.clickDesc("Collapse")
        Ui.waitForDesc("Expand")
        Waits.require("collapsed again (got ${messageCount()})") { messageCount() == 1 }
    }

    /** ROW-15: the same message under different tags, or with another log between copies, is not grouped. */
    @Test
    fun differentTagOrInterruptedRun_notGrouped() {
        openLogList {
            TestData.log("T1", "dup text", date = TestData.ts(50))
            TestData.log("T2", "dup text", date = TestData.ts(40))
            TestData.log("T1", "split text", date = TestData.ts(30))
            TestData.log("T3", "in between", date = TestData.ts(20))
            TestData.log("T1", "split text", date = TestData.ts(10))
        }
        Ui.waitForText("in between")
        Waits.require("both dup rows") { Ui.count(Ui.text("dup text")) == 2 }
        Waits.require("both split rows") { Ui.count(Ui.text("split text")) == 2 }
        Ui.assertNotShown("a group row") { it.contentDescription?.toString()?.startsWith("Repeated ") == true }
    }

    /**
     * SET-12: Settings › Log list › "Group repeated logs" is on by default; turning it off shows every copy as its
     * own row when returning to the list, and turning it back on groups them again.
     */
    @Test
    fun groupSetting_offShowsEveryCopy() {
        openLogList(::seedRun)
        Ui.waitForDesc("Repeated 3 times")

        overflow("Settings")
        Ui.waitForText("Log list")
        Ui.waitForText("Folds back-to-back copies of the same log into one row")
        val switch = Ui.waitForText("Group repeated logs").selfOrAncestor { it.isCheckable }!!
        assertEquals("on by default", true, switch.isChecked)
        Ui.click(switch)
        Waits.require("switch off") { Ui.waitForText("Group repeated logs").selfOrAncestor { it.isCheckable }?.isChecked == false }
        Ui.back()

        awaitLogList()
        Waits.require("three separate rows (got ${messageCount()})") { messageCount() == 3 }
        Ui.assertNotShown("group row", Ui.desc("Repeated 3 times"))

        overflow("Settings")
        Ui.click(Ui.waitForText("Group repeated logs").selfOrAncestor { it.isCheckable }!!)
        Ui.back()
        awaitLogList()
        Ui.waitForDesc("Repeated 3 times")
        Waits.require("grouped again (got ${messageCount()})") { messageCount() == 1 }
    }

    /**
     * GEN-03: a recreation (what rotation or a window resize does) keeps the selected chip and an expanded group,
     * and is not treated as a return (no new-logs pill).
     */
    @Test
    fun recreation_keepsSelectedFilterAndExpandedGroup() {
        openLogList(::seedRun)
        selectChip("Rep")
        Ui.waitForText("Contain text in log value")
        Ui.clickDesc("Expand")
        Waits.require("expanded") { messageCount() == 4 }

        val activity = AppState.resumedActivity()!!
        InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.recreate() }
        Waits.require("recreated") { AppState.resumedActivity().let { it != null && it !== activity } }

        awaitLogList()
        Ui.waitForText("Contain text in log value")
        Waits.require("still expanded after recreation (got ${messageCount()})") { messageCount() == 4 }
        Ui.waitForDesc("Collapse")
        Ui.assertNotShown("other tag's row", Ui.text("other message"))
        assertFalse("no new-logs pill", Ui.exists(Ui.textContains("new log")))
    }
}
