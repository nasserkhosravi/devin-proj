package com.khosravi.devin.present.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Presenter 4.11.0: pull to refresh on the log list and the client list (inventory section 13). */
@RunWith(AndroidJUnit4::class)
class PullToRefreshE2eTest : E2eTest() {

    /** LIST-33: pulling down the log list reloads it (a log written after opening appears) and toasts "refreshed". */
    @Test
    fun logList_pullDownShowsNewLogs() {
        openLogList { TestData.log("A", "first log", date = TestData.ts(30)) }
        Ui.waitForText("first log")

        TestData.log("A", "pulled in later", date = TestData.ts(10))
        Ui.assertNotShown("new log before the pull", Ui.text("pulled in later"))
        Ui.pullDown(Ui.bounds(Ui.waitForText("first log")).top)

        Ui.waitForText("pulled in later")
        Ui.waitForText("first log")
        Toasts.waitFor("refreshed")
    }

    /** LIST-33: an empty list (no logs yet) can still be pulled, and logs written since show up. */
    @Test
    fun logList_emptyListCanBePulled() {
        openLogList()
        TestData.log("A", "arrived while empty")

        Ui.pullDown(Ui.bounds(chip("All")).bottom + 150)

        Ui.waitForText("arrived while empty")
    }

    /** START-11: pulling down the client list refetches it; a client registered after opening appears. */
    @Test
    fun clientList_pullDownShowsNewClient() {
        val third = "e2e.client.c"
        TestData.client(TestData.CLIENT)
        TestData.client(TestData.CLIENT_B)
        launchStarter()
        Ui.waitForText("Choose client source")
        Ui.waitForText(TestData.CLIENT_B)

        TestData.client(third)
        Ui.assertNotShown("third client before the pull", Ui.text(third))
        Ui.pullDown(Ui.bounds(Ui.waitForText(TestData.CLIENT)).top)

        Ui.waitForText(third)
        Ui.waitForText(TestData.CLIENT)
        Ui.waitForText("Choose client source")
    }
}
