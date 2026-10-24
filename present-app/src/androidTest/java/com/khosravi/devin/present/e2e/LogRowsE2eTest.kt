package com.khosravi.devin.present.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.e2e.Ui.textValue
import com.khosravi.devin.read.DevinImageFlagsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogRowsE2eTest : E2eTest() {

    /**
     * ROW-02, ROW-03, ROW-04, LIST-03: every level gets its uppercase badge ("VERBOSE" for level 2), the row time
     * is `HH:mm:ss.123`, the tag chip and message are shown, newest first.
     */
    @Test
    fun textRows_showBadgeTimeWithMillisTagAndMessage() {
        val base = TestData.ts(secondsAgo = 600)
        val levels = listOf(Log.VERBOSE to "VERBOSE", Log.DEBUG to "DEBUG", Log.INFO to "INFO", Log.WARN to "WARN", Log.ERROR to "ERROR")
        openLogList {
            levels.forEachIndexed { i, (level, _) -> TestData.log("Lvl", "message level $level", date = base + i * 1000L, level = level) }
        }

        levels.forEach { (level, badge) ->
            Ui.waitForText("message level $level")
            Ui.waitForText(badge)
        }
        levels.indices.forEach { i -> Ui.waitForText(TestData.timeWithMillis(base + i * 1000L)) }
        assertTrue(Ui.count(Ui.textMatches(Regex("""\d\d:\d\d:\d\d\.123"""))) >= levels.size)
        // Newest first: the ERROR log (inserted last) is above the VERBOSE one.
        val top = Ui.bounds(Ui.waitForText("message level ${Log.ERROR}")).top
        val bottom = Ui.bounds(Ui.waitForText("message level ${Log.VERBOSE}")).top
        assertTrue("newest first", top < bottom)
        // Tag chip on every row ("All" selected) plus the filter chip.
        assertEquals(levels.size + 1, Ui.count(Ui.text("Lvl")))
    }

    /** ROW-01: one centred date pill per day, Persian calendar `yyyy-M-d`. */
    @Test
    fun dateHeaders_onePerDay() {
        val today = TestData.ts()
        val earlier = TestData.daysAgo(2)
        openLogList {
            TestData.log("A", "today log", date = today)
            TestData.log("A", "older log", date = earlier)
        }

        Ui.waitForText("today log")
        Ui.waitForText(TestData.persianDate(today))
        Ui.waitForText(TestData.persianDate(earlier))
        assertEquals(1, Ui.count(Ui.text(TestData.persianDate(today))))
    }

    /** ROW-09: HTTP row shows status code, "METHOD /path", "scheme://domain" and the time without millis. */
    @Test
    fun httpRow_showsStatusMethodPathDomainTime() {
        val date = TestData.ts()
        openLogList {
            TestData.http(TestData.Http(url = "https://api.example.com/v1/users?page=2", method = "POST", status = 201), date = date)
            TestData.http(TestData.Http(url = "http://other.example.org/missing", method = "GET", status = 404), date = date - 5_000)
        }

        Ui.waitForText("201")
        Ui.waitForText("POST /v1/users")
        Ui.waitForText("https://api.example.com")
        Ui.waitForText("404")
        Ui.waitForText("GET /missing")
        Ui.waitForText("http://other.example.org")
        Ui.waitForText(TestData.time(date))
        // The okhttp tag chip is labelled "http".
        chip("http")
    }

    /** ROW-13, ROW-14: session start row with "Session started", the parsed "v1.2.3" pill and date/time; not tappable. */
    @Test
    fun sessionStartRow_showsVersionPill() {
        val date = TestData.ts()
        openLogList { TestData.sessionStart("1.2.3", date = date) }

        val title = Ui.waitForText("Session started")
        Ui.waitForText("v1.2.3")
        Ui.waitForText(TestData.persianDate(date))
        Ui.waitForText(TestData.time(date))
        assertTrue("not clickable", title.selfOrAncestorClickable() == null)
    }

    /** ROW-11, ROW-12: a failed image shows "Status: Failed" with name/date/time; tapping the URL copies it. */
    @Test
    fun imageRow_failedShowsInfo_urlTapCopies() {
        val url = "https://img.example.com/avatar.png"
        val date = TestData.ts()
        openLogList { TestData.image("avatar", url, DevinImageFlagsApi.Status.FAILED, date = date) }

        val info = Ui.waitFor("image info", predicate = Ui.textContains("Status: Failed"))
        val text = info.textValue()!!
        assertTrue(text, text.contains("Name: avatar"))
        assertTrue(text, text.contains("Date: ${TestData.persianDate(date)}"))
        assertTrue(text, text.contains("Time: ${TestData.time(date)}"))

        Ui.click(Ui.waitFor("URL line", predicate = Ui.text("URL: $url")))
        Toasts.waitFor("URL Copied")
        Waits.require("clipboard holds the url") { Clipboard.text() == url }
    }

    /** ROW-06: a 5,000-character message is cut to a 2,000-character preview ending in "…". */
    @Test
    fun longMessage_rowShowsPreview() {
        val long = "x".repeat(4_990) + "END-MARKER"
        openLogList { TestData.log("A", long) }

        val row = Ui.waitFor("long row", predicate = Ui.textContains("xxxxxxxxxx"))
        val text = row.textValue()!!
        assertTrue("ends with ellipsis", text.endsWith("…"))
        assertEquals(2_001, text.length)
    }

    /** ROW-07: an unknown level (7) shows no badge. */
    @Test
    fun unknownLevel_noBadge() {
        openLogList { TestData.log("A", "assert level", level = 7) }

        Ui.waitForText("assert level")
        listOf("ERROR", "WARN", "INFO", "DEBUG", "VERBOSE").forEach { Ui.assertNotShown("badge $it", Ui.text(it)) }
    }

    /**
     * ROW-05: the row tag chip is shown on "All" and on a custom filter, hidden on a tag chip.
     * Counted as nodes with the tag's text: the filter chip itself is one of them.
     */
    @Test
    fun tagChip_shownOnAllAndCustom_hiddenOnTagFilter() {
        openLogList {
            TestData.log("Net", "net one", date = TestData.ts(30))
            TestData.log("Net", "net two", date = TestData.ts(20))
        }
        awaitMessages(listOf("net one", "net two"))
        Ui.clickDesc("Refresh")
        Waits.require("All: 2 row chips + 1 filter chip") { Ui.count(Ui.text("Net")) == 3 }

        selectChip("Net")
        Ui.waitForText("Contain text in log value")
        Waits.require("tag filter: only the filter chip") { Ui.count(Ui.text("Net")) == 1 && Ui.exists(Ui.text("net one")) }

        overflow("Create custom filter")
        pickFilterTag("Net")
        Ui.typeInto("title", "Mine")
        Ui.clickText("confirm")
        Ui.waitForText("Mine")
        Waits.require("custom filter: row chips visible again") { Ui.count(Ui.text("Net")) == 3 }
    }

    private fun android.view.accessibility.AccessibilityNodeInfo.selfOrAncestorClickable() =
        with(Ui) { selfOrAncestor { it.isClickable } }
}
