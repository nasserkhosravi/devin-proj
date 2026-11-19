package com.khosravi.devin.present.e2e

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Presenter 4.11.0: tapping a link in the log detail opens an Open/Copy menu (inventory section 13). */
@RunWith(AndroidJUnit4::class)
class LinkMenuE2eTest : E2eTest() {

    /** Opens the detail of a log whose message is [message] and returns the message node on the detail page. */
    private fun openDetail(message: String) = run {
        openLogList { TestData.log("Link", message) }
        Ui.clickText(message)
        Ui.waitForText("Log detail")
        Ui.waitForText("Message")
        Ui.waitForText(message)
    }

    /** Taps [message]'s first characters (the link starts there; the text node can be wider than the text). */
    private fun tapStartOf(message: String) {
        val r = Ui.bounds(Ui.waitForText(message))
        Ui.tapAt(r.left + 15, r.centerY())
    }

    /** DET-09, DET-10: a link tap shows "Open"/"Copy" and opens nothing; Open fires ACTION_VIEW for the link. */
    @Test
    fun tapLink_menuThenOpenFiresView() {
        IntentStubs.stubSystemUi()
        val url = "https://example.com/e2e-link"
        openDetail(url)

        tapStartOf(url)
        Ui.waitForText("Open")
        Ui.waitForText("Copy")
        Waits.holds("nothing opened before a choice", 800) { IntentStubs.started().none { it.action == Intent.ACTION_VIEW } }

        Ui.clickText("Open")
        val view = IntentStubs.waitForStarted("ACTION_VIEW of the link") { it.action == Intent.ACTION_VIEW }
        assertEquals(url, view.dataString)
        Ui.waitGone("menu", predicate = Ui.text("Copy"))
    }

    /** DET-11: Copy puts the link (not the whole message) on the clipboard; "Link copied" toasts only below Android 13. */
    @Test
    fun tapLink_copyPutsLinkOnClipboard() {
        val url = "https://example.com/copy-me"
        val message = "$url is where it failed"
        openDetail(message)

        tapStartOf(message)
        Ui.clickText("Copy")
        Waits.require("clipboard = link (got ${Clipboard.text()})") { Clipboard.text() == url }
        Ui.waitGone("menu", predicate = Ui.text("Open"))
        if (Build.VERSION.SDK_INT < 33) Toasts.waitFor("Link copied") else Toasts.assertNone("Link copied")
        assertEquals(0, IntentStubs.started().count { it.action == Intent.ACTION_VIEW })
    }

    /** DET-09: tapping plain text (not a link) shows no menu. */
    @Test
    fun tapPlainText_noMenu() {
        val message = "plain words first then https://example.com/later"
        openDetail(message)

        tapStartOf(message)
        Waits.holds("no link menu", 1_500) { !Ui.exists(Ui.text("Copy")) && !Ui.exists(Ui.text("Open")) }
    }

    /** DET-10: Open on a link no app handles toasts "No app can open this link" and closes the menu. */
    @Test
    fun openLinkWithoutHandler_toasts() {
        val url = "devine2enohandler://nothing/here"
        openDetail(url)

        tapStartOf(url)
        Ui.clickText("Open")
        Toasts.waitFor("No app can open this link")
        Ui.waitGone("menu", predicate = Ui.text("Copy"))
        Ui.waitForText("Log detail")
    }
}
