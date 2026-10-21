package com.khosravi.devin.present.e2e

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khosravi.devin.present.e2e.IntentStubs.chooserTarget
import com.khosravi.devin.present.e2e.Ui.textValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogListE2eTest : E2eTest() {

    /** LIST-01, LIST-02: title, the three actions and the five overflow items in order. */
    @Test
    fun topBarAndOverflowMenu() {
        openLogList()

        Ui.waitForDesc("Refresh")
        Ui.clickDesc("More options")
        val items = listOf("Share/Save logs", "Create custom filter", "Clear custom filters", "Toggle theme", "Settings")
        val tops = items.map { Ui.bounds(Ui.waitForText(it)).top }
        assertEquals("menu order", tops.sorted(), tops)
        Ui.back()
    }

    /** LIST-04: chips are "All", pinned (okhttp → "http", pinned by default), then the other tags. */
    @Test
    fun chips_allThenPinnedHttpThenTags() {
        openLogList {
            TestData.log("A", "a log")
            TestData.http()
        }

        Ui.waitForText("a log")
        Waits.require("chip order All, http, A (got ${chipLabels()})") { chipLabels().take(3) == listOf("All", "http", "A") }
    }

    /** LIST-06, LIST-20: a tag chip shows only that tag's logs plus the search row; "All" shows everything again. */
    @Test
    fun selectTagChip_filtersAndShowsSearchRow() {
        openLogList {
            TestData.log("A", "alpha value", date = TestData.ts(30))
            TestData.log("B", "beta value", date = TestData.ts(20))
        }
        awaitMessages(listOf("alpha value", "beta value"))
        Ui.assertNotShown("search row on All", Ui.text("Contain text in log value"))

        selectChip("A")
        Ui.waitForText("Contain text in log value")
        awaitMessages(listOf("alpha value"), absent = listOf("beta value"))

        selectChip("All")
        awaitMessages(listOf("alpha value", "beta value"))
        Ui.waitGone("search row", predicate = Ui.text("Contain text in log value"))
    }

    /** LIST-20: the okhttp tag's search hint is "Contain text in url"; LIST-21: it filters by url. */
    @Test
    fun httpChip_searchesUrl() {
        openLogList {
            TestData.http(TestData.Http(url = "https://api.example.com/users", method = "GET"), date = TestData.ts(30))
            TestData.http(TestData.Http(url = "https://api.example.com/orders", method = "GET"), date = TestData.ts(20))
        }
        selectChip("http")
        val search = Ui.waitForText("Contain text in url")
        awaitMessages(listOf("GET /users", "GET /orders"))

        Ui.setText(Ui.field("Contain text in url"), "orders")
        awaitMessages(listOf("GET /orders"), absent = listOf("GET /users"))
        assertTrue(search.text != null)
    }

    /** LIST-21, LIST-22: typing filters after the debounce; the clear (×) button restores the tag list. */
    @Test
    fun search_debouncedFilter_andClearButton() {
        openLogList {
            TestData.log("A", "alpha", date = TestData.ts(30))
            TestData.log("A", "beta", date = TestData.ts(20))
        }
        selectChip("A")
        awaitMessages(listOf("alpha", "beta"))

        Ui.setText(Ui.field("Contain text in log value"), "alp")
        awaitMessages(listOf("alpha"), absent = listOf("beta"))

        Ui.clickDesc("Clear search")
        awaitMessages(listOf("alpha", "beta"))
        Ui.waitForText("Contain text in log value")
    }

    /** LIST-23, LIST-24: a search keeps applying to later pages (60 logs, 30 match, 40 per page). */
    @Test
    fun search_appliesAcrossPages() {
        openLogList {
            TestData.logs(60, "A") { i -> if (i % 2 == 0) "item $i x-match" else "item $i other" }
        }
        selectChip("A")
        Ui.waitForText("item 59 other")

        Ui.setText(Ui.field("Contain text in log value"), "x-match")
        Ui.waitGone("non-matching rows", predicate = Ui.text("item 59 other"))
        val seen = collectRowsScrollingDown { it.startsWith("item ") }
        assertTrue("every row matches: $seen", seen.all { it.endsWith("x-match") })
        assertEquals("all 30 matches reachable", 30, seen.size)
    }

    /** LIST-24: scrolling to the end pages in older logs until the oldest is shown. */
    @Test
    fun pagination_scrollReachesOldestLog() {
        openLogList { TestData.logs(100, "P") { i -> "page log $i" } }
        Ui.waitForText("page log 99")

        val seen = collectRowsScrollingDown { it.startsWith("page log ") }
        assertTrue("oldest reached", seen.contains("page log 0"))
        assertEquals(100, seen.size)
    }

    /** LIST-14, LIST-15, LIST-16: empty dialog stays open; title + search creates a selected, filtering chip. */
    @Test
    fun createCustomFilter_validationAndConfirm() {
        openLogList {
            TestData.log("A", "boom happened", date = TestData.ts(30))
            TestData.log("A", "all quiet", date = TestData.ts(20))
        }
        awaitMessages(listOf("boom happened", "all quiet"))

        overflow("Create custom filter")
        Ui.waitForText("title")
        Ui.waitForText("contain tag")
        Ui.waitForText("search value")
        Ui.clickText("confirm")
        Waits.holds("dialog stays open when title and tag are empty", 1_000) { Ui.exists(Ui.text("confirm")) }

        Ui.typeInto("title", "Errs")
        Ui.typeInto("search value", "boom")
        Ui.clickText("confirm")

        Ui.waitGone("dialog", predicate = Ui.text("confirm"))
        chip("Errs")
        awaitMessages(listOf("boom happened"), absent = listOf("all quiet"))
    }

    /** LIST-17: a custom filter titled like an existing tag doesn't crash; both chips are shown. */
    @Test
    fun customFilterNamedLikeTag_noCrash() {
        openLogList { TestData.log("A", "a value") }
        Ui.waitForText("a value")

        overflow("Create custom filter")
        Ui.typeInto("title", "A")
        Ui.clickText("confirm")

        Waits.require("two chips labelled A") { chipLabels().count { it == "A" } == 2 }
        Ui.waitForDesc("Clear all logs")
        Ui.waitForText("a value")
    }

    /** LIST-18: back closes the filter dialog without adding a filter. */
    @Test
    fun filterDialog_backDismisses() {
        openLogList { TestData.log("A", "a value") }
        overflow("Create custom filter")
        Ui.typeInto("title", "Nope")
        Ui.back()
        Ui.waitGone("dialog", predicate = Ui.text("confirm"))
        Ui.assertNotShown("chip Nope", Ui.text("Nope"))
    }

    /** LIST-25: refresh picks up new logs and toasts "refreshed". */
    @Test
    fun refresh_reloadsAndToasts() {
        openLogList { TestData.log("A", "first") }
        Ui.waitForText("first")

        TestData.log("A", "added later")
        Ui.clickDesc("Refresh")

        Ui.waitForText("added later")
        Toasts.waitFor("refreshed")
    }

    /** LIST-26, LIST-27: clear deletes the client's logs without confirmation; list blank, only "All" left. */
    @Test
    fun clearLogs_deletesFromProvider() {
        openLogList {
            TestData.log("A", "doomed one")
            TestData.log("B", "doomed two")
        }
        awaitMessages(listOf("doomed one", "doomed two"))

        Ui.clickDesc("Clear all logs")

        awaitMessages(emptyList(), absent = listOf("doomed one", "doomed two"))
        Waits.require("provider empty") { TestData.logCount() == 0L }
        Waits.require("only All chip (got ${chipLabels()})") { chipLabels() == listOf("All") }
    }

    /** LIST-19, LIST-10, LIST-13: custom chip menu has Pin + Remove; remove deletes it; clear removes all custom chips. */
    @Test
    fun customFilters_removeAndClearAll() {
        openLogList { TestData.log("A", "a value") }
        listOf("F1", "F2", "F3").forEach { title ->
            overflow("Create custom filter")
            Ui.typeInto("title", title)
            Ui.typeInto("contain tag", "A")
            Ui.clickText("confirm")
            chip(title)
        }

        chipMenu("F1")
        Ui.waitForText("Pin")
        Ui.assertNotShown("Share as json on a custom chip", Ui.text("Share as json"))
        Ui.clickText("Remove")
        Ui.waitGone("chip F1", predicate = Ui.text("F1"))

        overflow("Clear custom filters")
        Ui.waitGone("custom chips") { it.textValue() == "F2" || it.textValue() == "F3" }
        Ui.waitForText("a value")

        relaunchApp()
        awaitLogList()
        Ui.waitForText("a value")
        Ui.assertNotShown("cleared filters stay cleared") { it.textValue() in setOf("F1", "F2", "F3") }
    }

    /** LIST-08: long-pressing "All" opens no menu. */
    @Test
    fun longPressAll_noMenu() {
        openLogList()
        val all = chip("All")
        assertTrue("All is not long-clickable", with(Ui) { all.selfOrAncestor { it.isLongClickable } } == null)
    }

    /**
     * LIST-09, LIST-11, SET-07: tag chip menu has Pin + Share as json; a pinned chip goes to the pinned group
     * (right after "All"), the pin survives a relaunch and is listed in Settings; Unpin restores the old order
     * after a relaunch. (The pin icon has no text/description, so order is what's asserted.)
     */
    @Test
    fun pinTagChip_persistsAndShowsInSettings() {
        openLogList {
            TestData.log("Apple", "apple log", date = TestData.ts(30))
            TestData.log("Zed", "zed log", date = TestData.ts(20))
        }
        Ui.waitForText("zed log")
        val original = chipLabels().filter { it == "Apple" || it == "Zed" }
        val target = original.last()
        val other = original.first()

        chipMenu(target)
        Ui.waitForText("Share as json")
        Ui.assertNotShown("Remove on a tag chip", Ui.text("Remove"))
        Ui.clickText("Pin")
        Waits.require("$target moved before $other (got ${chipLabels()})") { chipLabels().take(3) == listOf("All", target, other) }

        relaunchApp()
        awaitLogList()
        Waits.require("pin survives relaunch (got ${chipLabels()})") { chipLabels().take(3) == listOf("All", target, other) }

        overflow("Settings")
        Ui.waitForText("Pinned filter tags")
        Ui.waitForDesc("Unpin $target")
        Ui.back()
        awaitLogList()

        chipMenu(target)
        Ui.clickText("Unpin")
        relaunchApp()
        awaitLogList()
        Waits.require("original order after unpin (got ${chipLabels()})") { chipLabels().take(3) == listOf("All") + original }
    }

    /** LIST-12: "Share as json" opens the "Share file through" chooser with an application/json file. */
    @Test
    fun shareTagAsJson_opensChooserWithJson() {
        IntentStubs.stubSystemUi()
        openLogList { TestData.log("A", "a value") }
        Ui.waitForText("a value")

        chipMenu("A")
        Ui.clickText("Share as json")

        val chooser = IntentStubs.waitForStarted("share chooser") { it.action == Intent.ACTION_CHOOSER }
        assertEquals("Share file through", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        val send = chooser.chooserTarget()!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/json", send.type)
    }

    /**
     * Scrolls the log list to the end and returns every distinct row message matching [accept], in order seen.
     * Pagination appends while scrolling; stops when a scroll reveals nothing new.
     */
    private fun collectRowsScrollingDown(accept: (String) -> Boolean): List<String> {
        val seen = LinkedHashSet<String>()
        var idle = 0
        while (idle < 4) {
            val before = seen.size
            Ui.findAll { n -> n.textValue()?.let(accept) == true }.forEach { seen += it.textValue()!! }
            val list = Ui.find { it.isScrollable && Ui.bounds(it).height() > 600 }
            val scrolled = list?.let { Ui.scrollForward(it) } ?: false
            // Give the next page (and recomposition) time to arrive before deciding nothing changed.
            Waits.until(3_000) { Ui.findAll { n -> n.textValue()?.let(accept) == true }.any { it.textValue() !in seen } }
            idle = if (seen.size == before || !scrolled) idle + 1 else 0
        }
        return seen.toList()
    }
}
