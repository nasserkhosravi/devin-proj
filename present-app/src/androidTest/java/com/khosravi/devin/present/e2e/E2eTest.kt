package com.khosravi.devin.present.e2e

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.khosravi.devin.present.present.StarterActivity
import org.junit.After
import org.junit.Before

/**
 * Base for every e2e test: fresh app state before, everything closed after. Interaction goes through [Ui]
 * (accessibility tree), so tests only rely on what the user can see: texts and content descriptions.
 */
abstract class E2eTest {

    protected val context: Context get() = AppState.context

    @Before
    fun e2eSetUp() {
        // The app's notification permission dialog must never appear; tests that need notifications grant it.
        grantNotificationPermission()
        Ui.dismissSystemAnrDialog()
        AppState.reset()
        Toasts.start()
        IntentStubs.install()
    }

    @After
    fun e2eTearDown() {
        IntentStubs.uninstall()
        AppState.finishAllActivities()
    }

    /** Launches the app like the launcher does (a new task). */
    protected fun launchStarter(configure: Intent.() -> Unit = {}) {
        launch(Intent(context, StarterActivity::class.java).apply(configure))
    }

    /**
     * Starts [intent] in a new task and waits until one of the app's activities is resumed. On the slow emulator a
     * System UI / launcher "isn't responding" dialog can swallow the start; then it is dismissed and retried.
     */
    protected fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        repeat(3) {
            context.startActivity(intent)
            if (Waits.until(20_000) { AppState.resumedActivity() != null }) return
            Ui.dismissSystemAnrDialog()
        }
        throw AssertionError("Activity never resumed: $intent\n${Ui.dump()}")
    }

    /** Seeds one client with [logs] and opens its log list (auto-route), waiting for the "All" chip. */
    protected fun openLogList(seed: () -> Unit = {}) {
        TestData.client()
        seed()
        launchStarter()
        awaitLogList()
    }

    protected fun awaitLogList() {
        Ui.waitForText("Devin")
        Ui.waitForText("All")
        Ui.waitForDesc("Clear all logs")
    }

    // region log list helpers
    /** The filter chip labelled [label]: the chip row comes before the list, so it is the first such text. */
    protected fun chip(label: String) = Ui.waitFor("chip \"$label\"", predicate = Ui.text(label))

    protected fun selectChip(label: String) = Ui.click(chip(label))

    protected fun chipMenu(label: String) = Ui.longClick(chip(label))

    /** Enters tag selection mode from [first]'s long-press menu ("Select multiple"), then taps [more] to add them. */
    protected fun selectChips(first: String, vararg more: String) {
        chipMenu(first)
        Ui.clickText("Select multiple")
        Ui.waitForText("1 selected")
        more.forEachIndexed { index, label ->
            selectChip(label)
            Ui.waitForText("${index + 2} selected")
        }
    }

    /**
     * Picks [tag] in the open filter dialog. The dialog is the topmost window, so its tag chip is found before the
     * filter-bar chip of the same name; pick tags before typing a title that equals one.
     */
    protected fun pickFilterTag(tag: String) = Ui.clickText(tag)

    /** Left-to-right labels of the filter chips (the clickable pills above the list). */
    protected fun chipLabels(): List<String> {
        val all = Ui.waitFor("chip All", predicate = Ui.text("All"))
        val rowTop = Ui.bounds(all).top
        return Ui.findAll { n -> n.text != null && Ui.bounds(n).let { it.top in (rowTop - 30)..(rowTop + 30) } }
            .sortedBy { Ui.bounds(it).left }
            .map { it.text.toString() }
    }

    protected fun overflow(item: String) {
        Ui.clickDesc("More options")
        Ui.clickText(item)
    }

    /** Waits until the list shows exactly these message texts somewhere (visible) and none of [absent]. */
    protected fun awaitMessages(present: List<String>, absent: List<String> = emptyList()) {
        Waits.require("messages $present visible and $absent gone\n${Ui.dump()}") {
            present.all { Ui.exists(Ui.text(it)) } && absent.none { Ui.exists(Ui.text(it)) }
        }
    }
    // endregion

    protected fun relaunchApp() {
        AppState.finishAllActivities()
        launchStarter()
    }

    private fun grantNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
