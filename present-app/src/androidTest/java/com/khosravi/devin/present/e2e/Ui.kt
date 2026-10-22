package com.khosravi.devin.present.e2e

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Black-box driver over the accessibility tree (the same tree UiAutomator reads). Compose exposes its
 * unmerged semantics there: every `Text` is a node with `text`, icons carry `contentDescription`, clickable /
 * long-clickable / editable / checkable state comes from the semantics modifiers.
 *
 * Nodes are looked up in every interactive window of the app (activity, dialogs, popups, bottom sheets).
 * By default only nodes visible to the user count, so composed-but-off-screen pager pages don't match.
 */
object Ui {

    const val PACKAGE = "com.khosravi.devin.present"

    val automation: UiAutomation by lazy {
        InstrumentationRegistry.getInstrumentation().uiAutomation.apply {
            serviceInfo = serviceInfo.apply {
                flags = flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                // Don't coalesce events of one type (a toast must not be replaced by a later notification event).
                notificationTimeout = 0
            }
        }
    }

    // region matchers
    fun text(value: String): (AccessibilityNodeInfo) -> Boolean = { it.textValue() == value }
    fun desc(value: String): (AccessibilityNodeInfo) -> Boolean = { it.contentDescription?.toString() == value }
    fun textOrDesc(value: String): (AccessibilityNodeInfo) -> Boolean = { it.textValue() == value || it.contentDescription?.toString() == value }
    fun textContains(value: String): (AccessibilityNodeInfo) -> Boolean = { it.textValue()?.contains(value) == true }
    fun textMatches(regex: Regex): (AccessibilityNodeInfo) -> Boolean = { it.textValue()?.let(regex::matches) == true }
    val editable: (AccessibilityNodeInfo) -> Boolean = { it.isEditable }
    // endregion

    fun AccessibilityNodeInfo.textValue(): String? = text?.toString()

    /** All matching nodes in the app's windows, in tree (reading) order. */
    fun findAll(visibleOnly: Boolean = true, predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            if ((!visibleOnly || node.isVisibleToUser) && runCatching { predicate(node) }.getOrDefault(false)) result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        // Depth-first, children in order, so the result follows the on-screen order.
        appRoots().forEach(::walk)
        return result
    }

    fun find(visibleOnly: Boolean = true, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? =
        findAll(visibleOnly, predicate).firstOrNull()

    fun exists(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean = find(predicate = predicate) != null

    fun count(predicate: (AccessibilityNodeInfo) -> Boolean): Int = findAll(predicate = predicate).size

    fun waitFor(
        description: String,
        timeoutMs: Long = Waits.DEFAULT_TIMEOUT_MS,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        var polls = 0
        if (!Waits.until(timeoutMs) {
                if (++polls % 10 == 0) dismissSystemAnrDialog()
                find(predicate = predicate).also { found = it } != null
            }
        ) {
            throw AssertionError("Timed out after $timeoutMs ms waiting for node: $description\n--- screen ---\n${dump()}")
        }
        return found!!
    }

    fun waitForText(value: String, timeoutMs: Long = Waits.DEFAULT_TIMEOUT_MS) = waitFor("text \"$value\"", timeoutMs, text(value))

    fun waitForDesc(value: String, timeoutMs: Long = Waits.DEFAULT_TIMEOUT_MS) = waitFor("description \"$value\"", timeoutMs, desc(value))

    fun waitGone(description: String, timeoutMs: Long = Waits.DEFAULT_TIMEOUT_MS, predicate: (AccessibilityNodeInfo) -> Boolean) {
        if (!Waits.until(timeoutMs) { find(predicate = predicate) == null }) {
            throw AssertionError("Timed out after $timeoutMs ms waiting for node to disappear: $description\n--- screen ---\n${dump()}")
        }
    }

    fun assertNotShown(description: String, predicate: (AccessibilityNodeInfo) -> Boolean) {
        val node = find(predicate = predicate)
        if (node != null) throw AssertionError("Expected no node for: $description, found ${node.describe()}\n--- screen ---\n${dump()}")
    }

    // region actions
    fun click(node: AccessibilityNodeInfo) {
        node.refresh()
        val target = node.selfOrAncestor { it.isClickable && it.isEnabled }
        // A node whose parent chain isn't reported (e.g. inside a list that is still settling) gets a real tap.
        if (target == null || !target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) tap(node)
    }

    fun clickText(value: String, timeoutMs: Long = Waits.DEFAULT_TIMEOUT_MS) = click(waitForText(value, timeoutMs))

    fun clickDesc(value: String, timeoutMs: Long = Waits.DEFAULT_TIMEOUT_MS) = click(waitForDesc(value, timeoutMs))

    fun longClick(node: AccessibilityNodeInfo) {
        val target = node.selfOrAncestor { it.isLongClickable }
            ?: throw AssertionError("No long-clickable node at/above ${node.describe()}")
        check(target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) { "Long click failed on ${target.describe()}" }
    }

    /** A real touch at the node's centre (for behaviour that depends on the touch pipeline). */
    fun tap(node: AccessibilityNodeInfo) {
        val r = Rect().also(node::getBoundsInScreen)
        tapAt(r.centerX(), r.centerY())
    }

    fun tapAt(x: Int, y: Int) = shell("input tap $x $y")

    fun setText(node: AccessibilityNodeInfo, value: String) {
        val target = node.selfOrAncestor { it.isEditable } ?: node.firstDescendant { it.isEditable }
            ?: throw AssertionError("No editable node at/near ${node.describe()}")
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        check(target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { "Set text failed on ${target.describe()}" }
    }

    /** The editable text field whose label/placeholder (a descendant or sibling text) is [label]. */
    fun field(label: String, timeoutMs: Long = Waits.DEFAULT_TIMEOUT_MS): AccessibilityNodeInfo {
        val labelNode = waitForText(label, timeoutMs)
        return labelNode.selfOrAncestor { it.isEditable }
            ?: labelNode.selfOrAncestor { ancestor -> ancestor.firstDescendant { it.isEditable } != null }?.firstDescendant { it.isEditable }
            ?: throw AssertionError("No text field labelled \"$label\"\n${dump()}")
    }

    fun typeInto(label: String, value: String) = setText(field(label), value)

    /** Triggers the IME action (Done/Search) of an editable node, like pressing the keyboard's action key. */
    fun imeAction(node: AccessibilityNodeInfo) {
        val target = node.selfOrAncestor { it.isEditable } ?: node
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val ime = AccessibilityAction.ACTION_IME_ENTER
        check(target.performAction(ime.id)) { "IME action failed on ${target.describe()}" }
    }

    fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        val target = node.selfOrAncestor { it.isScrollable } ?: node.firstDescendant { it.isScrollable } ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun firstScrollable(): AccessibilityNodeInfo? = find { it.isScrollable && it.className?.contains("HorizontalScroll") != true && it.isVerticalList() }

    fun back() {
        check(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)) { "Global back failed" }
    }

    fun shell(command: String): String {
        val pfd: ParcelFileDescriptor = automation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }
    // endregion

    /**
     * The emulator can be slow enough for System UI to raise an "isn't responding" dialog on top of the app;
     * answer "Wait" (it keeps everything running) so it doesn't cover the app.
     */
    fun dismissSystemAnrDialog() {
        for (window in automation.windows) {
            val root = window.root ?: continue
            if (root.packageName?.toString() != "android") continue
            val wait = root.findAccessibilityNodeInfosByViewId("android:id/aerr_wait").firstOrNull() ?: continue
            wait.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    private fun AccessibilityNodeInfo.isVerticalList(): Boolean {
        val info = collectionInfo ?: return true
        return info.columnCount <= 1
    }

    fun AccessibilityNodeInfo.selfOrAncestor(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = this
        while (node != null) {
            if (predicate(node)) return node
            node = node.parent
        }
        return null
    }

    fun AccessibilityNodeInfo.firstDescendant(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        for (i in 0 until childCount) {
            val child = getChild(i) ?: continue
            if (predicate(child)) return child
            child.firstDescendant(predicate)?.let { return it }
        }
        return null
    }

    private fun appRoots(): List<AccessibilityNodeInfo> {
        // The in-process node cache can keep texts of recycled lazy-list rows; always read the live tree.
        if (android.os.Build.VERSION.SDK_INT >= 34) automation.clearCache()
        // Topmost window first: dialogs/popups/sheets before the activity under them.
        val roots = automation.windows.sortedByDescending { it.layer }.mapNotNull { it.root }
            .filter { it.packageName?.toString() == PACKAGE }
        // The window list can lag behind a just-opened activity; the active window's root covers that gap.
        val active = automation.rootInActiveWindow?.takeIf { it.packageName?.toString() == PACKAGE }
        return if (active == null || roots.any { it.windowId == active.windowId }) roots else roots + active
    }

    fun AccessibilityNodeInfo.describe(): String {
        val flags = buildList {
            if (isClickable) add("click")
            if (isLongClickable) add("long")
            if (isEditable) add("edit")
            if (isCheckable) add(if (isChecked) "checked" else "unchecked")
            if (isSelected) add("selected")
            if (!isEnabled) add("disabled")
            if (isScrollable) add("scroll")
            if (!isVisibleToUser) add("hidden")
        }
        return "[${className?.toString()?.substringAfterLast('.')} text=${text?.toString()?.take(80)?.replace("\n", "\\n")} " +
            "desc=$contentDescription ${flags.joinToString(",")} ${bounds(this).toShortString()}]"
    }

    /** Text dump of the app's windows for failure messages. */
    fun dump(): String = buildString {
        automation.windows.forEach { append("window ${it.id} type=${it.type} layer=${it.layer} title=${it.title} pkg=${it.root?.packageName}\n") }
        for (root in appRoots()) {
            fun walk(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 40) return
                if (node.text != null || node.contentDescription != null || node.isClickable || node.isEditable) {
                    append("  ".repeat(depth)).append(node.describe()).append('\n')
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
            }
            append("== window ").append(root.windowId).append('\n')
            walk(root, 0)
        }
    }
}
