package com.khosravi.devin.present.e2e

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.Stage
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

    /**
     * Labels of the filter chips (the clickable pills above the list), in chip order. With more than 4 chips the bar
     * is a 2-row horizontally scrolling grid filled column by column, so chip order is left-to-right across both rows.
     * A chip is a text whose parent is a narrow clickable pill near the "All" chip (log rows span the full width).
     */
    protected fun chipLabels(): List<String> {
        val all = Ui.waitFor("chip All", predicate = Ui.text("All"))
        val allBounds = Ui.bounds(all)
        val band = (allBounds.height() * 2.5).toInt()
        val maxPillWidth = Ui.screenWidth() * 2 / 3
        return Ui.findAll { n ->
            n.text != null && Ui.bounds(n).top in (allBounds.top - band)..(allBounds.top + band) &&
                n.parent?.let { p -> p.isClickable && Ui.bounds(p).width() < maxPillWidth } == true
        }
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

    /**
     * Leaves the app with Home (its activities stop, the task stays), runs [whileAway], then comes back like a launcher
     * tap: starting the task's root intent ([launchStarter]'s, without `CLEAR_TASK`) with `NEW_TASK` only brings the
     * existing task to the front, with the same activity instances (no new log list).
     */
    protected fun leaveAppAndReturn(whileAway: () -> Unit) {
        val before = AppState.resumedActivity()
        Ui.home()
        Waits.require("app in background") { AppState.resumedActivity() == null && AppState.activitiesIn(Stage.STOPPED).isNotEmpty() }
        whileAway()
        context.startActivity(Intent(context, StarterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Waits.require("same activity resumed again") { AppState.resumedActivity().let { it != null && it === before } }
    }

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
