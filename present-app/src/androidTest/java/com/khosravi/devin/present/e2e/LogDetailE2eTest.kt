package com.khosravi.devin.present.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import com.khosravi.devin.present.e2e.Ui.textValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogDetailE2eTest : E2eTest() {

    /** ROW-08, DET-01, DET-02: tapping a row opens "Log detail" with Tag/Time(.123)/Message/Meta; copy puts all four lines on the clipboard. */
    @Test
    fun detail_showsFieldsAndCopies() {
        val date = TestData.ts()
        val meta = JsonObject().apply { addProperty("log_level", Log.WARN); addProperty("userId", 42) }
        openLogList { TestData.log("Net", "request failed badly", date = date, meta = meta) }

        Ui.clickText("request failed badly")

        Ui.waitForText("Log detail")
        listOf("Tag", "Time", "Message", "Meta").forEach { Ui.waitForText(it) }
        val time = TestData.timeWithMillis(date)
        Ui.waitForText(time)
        Ui.waitForText("request failed badly")
        Ui.waitForText(meta.toString())

        Ui.clickDesc("Copy log detail")
        Toasts.waitFor("Copied")
        val expected = "Tag: Net\nTime: $time\nMessage: request failed badly\nMeta: $meta\n"
        Waits.require("clipboard = detail text (got ${Clipboard.text()})") { Clipboard.text() == expected }
    }

    /** DET-01: a log without meta shows no "Meta" label. */
    @Test
    fun detail_withoutMeta_hasNoMetaRow() {
        openLogList { TestData.log("Plain", "no meta here") }
        Ui.clickText("no meta here")
        Ui.waitForText("Log detail")
        Ui.waitForText("Message")
        Ui.assertNotShown("Meta label", Ui.text("Meta"))
    }

    /** DET-07, ROW-06: the detail page shows the full long message (the row shows a preview). */
    @Test
    fun detail_showsFullLongMessage() {
        val long = "y".repeat(4_990) + "END-MARKER"
        openLogList { TestData.log("A", long) }
        Ui.click(Ui.waitFor("long row", predicate = Ui.textContains("yyyyyyyy")))
        Ui.waitForText("Log detail")
        // The message is drawn in chunks inside a scrolling column; scroll until its end is reached.
        val found = Waits.until {
            Ui.exists(Ui.textContains("END-MARKER")) || run {
                Ui.find { it.isScrollable }?.let(Ui::scrollForward)
                false
            }
        }
        assertTrue("full text incl. END-MARKER", found)
    }

    /**
     * DET-03, DET-04, LIST-32: back (arrow or system) returns to the list with the selected chip, the search
     * text and the scroll position kept.
     */
    @Test
    fun back_keepsListState() {
        openLogList {
            TestData.logs(40, "A") { i -> if (i % 4 == 3) "noise $i" else "row $i" }
            TestData.log("B", "other tag", date = TestData.ts(5))
        }
        selectChip("A")
        Ui.setText(Ui.field("Contain text in log value"), "row")
        Ui.waitGone("other tag hidden by the tag filter", predicate = Ui.text("other tag"))
        Ui.waitGone("noise filtered by the search") { it.textValue()?.startsWith("noise") == true }
        val list = Ui.find { it.isScrollable && Ui.bounds(it).height() > 600 }!!
        Ui.scrollForward(list)
        Waits.require("scrolled away from the top") { !Ui.exists(Ui.text("row 38")) }
        val firstVisible = firstVisibleRow()

        Ui.click(Ui.waitForText(firstVisible))
        Ui.waitForText("Log detail")
        Ui.clickDesc("Navigate up")
        Waits.require("same first row after the back arrow") { firstVisibleRow() == firstVisible }

        Ui.click(Ui.waitForText(firstVisible))
        Ui.waitForText("Log detail")
        Ui.back()
        Waits.require("same first row after system back") { firstVisibleRow() == firstVisible }
        // The search row scrolled off with the list; the kept query shows as "noise" rows staying filtered out.
        Ui.assertNotShown("search text kept", Ui.textContains("noise"))
        Ui.assertNotShown("tag filter kept", Ui.text("other tag"))
    }

    /** DET-06: a double tap on a row opens a single detail page (one back returns to the list). */
    @Test
    fun doubleTap_opensOneDetail() {
        openLogList { TestData.log("A", "tap me twice") }
        val row = Ui.waitForText("tap me twice")
        Ui.tap(row)
        Ui.tap(row)
        Ui.waitForText("Log detail")
        Ui.back()
        awaitLogList()
        Ui.waitForText("tap me twice")
        Ui.assertNotShown("no second detail page", Ui.text("Log detail"))
    }

    private fun firstVisibleRow(): String =
        Ui.findAll(predicate = Ui.textMatches(Regex("row \\d+"))).minByOrNull { Ui.bounds(it).top }!!.textValue()!!
}
