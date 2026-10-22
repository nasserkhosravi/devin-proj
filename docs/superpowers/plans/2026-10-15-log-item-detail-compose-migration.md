# LogItemDetail Compose Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the XML `LogItemDetailActivity` with a shared Compose `LogItemDetailScreen`, shown as a navigation-compose destination inside `LogActivity`, while `ImportLogActivity` keeps reaching it through a thin bridge Activity.

**Architecture:** `LogActivity`'s `setContent` gains a `NavHost` with `"logs"` (existing `LogScreen`) and `"logDetail"` destinations. The detail destination receives tag/message/timestamp/meta as URL-encoded route query arguments, owned by a single `LogDetailArgs` object. `LogItemDetailActivity` becomes `setContent { LogItemDetailScreen(...) }` with its Intent API unchanged. Copy-to-clipboard logic is shared via `Context.copyLogDetail(...)`.

**Tech Stack:** Kotlin 1.9.0, Compose compiler 1.5.2, Compose BOM 2023.09.02 (Compose UI 1.5.2, Material3 1.1.2), `material-icons-core`, **new:** `androidx.navigation:navigation-compose:2.7.7`.

**Spec:** `docs/superpowers/specs/2026-10-15-log-item-detail-compose-migration-design.md`

> **Executed 2026-10-15.** Code review changed Tasks 2, 4 and 6 beyond the code shown here (value prefix, long-text chunking, RTL back arrow, `navigate_up`, `DisableSelection`, double-tap guard, `popBackStack(ROUTE_LOGS, false)`, NavHost split into member composables). See the spec's "Changes from review" section; the code is the source of truth. After device testing, Task 2/6 navigation was switched from URI route args to a Bundle by destination id (`LogDetailArgs.navigate`); `route()`/`VALUE_PREFIX` no longer exist.

**Testing note:** No unit/instrumented tests are added (AGENTS.md: tests need explicit go-ahead). Each task ends with a compile check; behaviour is verified on the emulator in Task 7. No commit steps — commits happen only when the user says so.

**Build command used throughout** (from the worktree root, JDK 17 required):

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug -q
```

Expected for every compile check: exits 0, no `e:` lines. (A `DevinDB` Room warning in the output is pre-existing and harmless.)

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | Add `navigationCompose` version + `navigation-compose` library |
| `present-app/build.gradle.kts` | Modify | Depend on `libs.navigation.compose` |
| `present-app/src/main/java/com/khosravi/devin/present/present/LogDetailArgs.kt` | Create | Route pattern, nav arguments, build route from `TextLogItemData`, parse back |
| `present-app/src/main/java/com/khosravi/devin/present/present/LogItemDetailCopy.kt` | Create | `Context.copyLogDetail(...)` — copy text + toast |
| `present-app/src/main/java/com/khosravi/devin/present/present/LogItemDetailScreen.kt` | Create | Stateless detail UI |
| `present-app/src/main/java/com/khosravi/devin/present/present/LogItemDetailActivity.kt` | Rewrite | Thin bridge host for `ImportLogActivity` |
| `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt` | Modify | `NavHost` with `"logs"` + `"logDetail"` |
| `present-app/src/main/res/layout/activity_log_item_detail.xml` | Delete | Replaced by Compose |
| `present-app/src/main/res/menu/log_item_detail_menu.xml` | Delete | Replaced by Compose top bar action |

---

### Task 1: Add navigation-compose dependency

**Files:**
- Modify: `gradle/libs.versions.toml` (versions block near line 30, libraries block near line 63)
- Modify: `present-app/build.gradle.kts:112`

- [ ] **Step 1: Add the version**

In `gradle/libs.versions.toml`, directly after `activityCompose = "1.7.2"` add:

```toml
navigationCompose = "2.7.7"
```

- [ ] **Step 2: Add the library**

In the `[libraries]` block, directly after the `compose-material-icons-core = ...` line add:

```toml
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
```

- [ ] **Step 3: Add the dependency**

In `present-app/build.gradle.kts`, directly after `implementation(libs.compose.material.icons.core)` add:

```kotlin
    implementation(libs.navigation.compose)
```

- [ ] **Step 4: Verify the resolved versions**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:dependencies --configuration debugRuntimeClasspath -q | grep -E "androidx.compose.ui:ui:|navigation-compose|androidx.compose.runtime:runtime:" | sort -u
```

Expected: every `androidx.compose.ui:ui` and `androidx.compose.runtime:runtime` line resolves to **1.5.2** (e.g. `androidx.compose.ui:ui:1.5.1 -> 1.5.2` is fine; anything resolving to 1.6+ is a failure), and `androidx.navigation:navigation-compose:2.7.7`. If Compose resolves above 1.5.2, STOP and report — do not bump the BOM or compiler.

- [ ] **Step 5: Compile check** — run the build command. Expected: exit 0.

---

### Task 2: `LogDetailArgs` — route owner

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/LogDetailArgs.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.khosravi.devin.present.present

import android.net.Uri
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.khosravi.devin.present.log.TextLogItemData

/**
 * Single owner of the `logDetail` navigation route used by [LogActivity]'s NavHost.
 * Carries the same values [LogItemDetailActivity] receives as Intent extras.
 */
object LogDetailArgs {
    private const val ROUTE_BASE = "logDetail"

    private const val ARG_TAG = "tag"
    private const val ARG_MESSAGE = "message"
    private const val ARG_TIMESTAMP = "timestamp"
    private const val ARG_META = "meta"

    /**
     * Prepended to every string value in the route and stripped in [read]. Without it,
     * NavType.StringType parses the literal "null" to a real null (crashing non-nullable args),
     * and a value equal to its own placeholder (e.g. "{message}") is silently dropped by NavDeepLink.
     */
    private const val VALUE_PREFIX = "_"

    const val ROUTE_PATTERN =
        "$ROUTE_BASE?$ARG_TAG={$ARG_TAG}&$ARG_MESSAGE={$ARG_MESSAGE}&$ARG_TIMESTAMP={$ARG_TIMESTAMP}&$ARG_META={$ARG_META}"

    val arguments: List<NamedNavArgument> = listOf(
        navArgument(ARG_TAG) { type = NavType.StringType; defaultValue = "" },
        navArgument(ARG_MESSAGE) { type = NavType.StringType; defaultValue = "" },
        navArgument(ARG_TIMESTAMP) { type = NavType.LongType },
        navArgument(ARG_META) { type = NavType.StringType; nullable = true; defaultValue = null },
    )

    fun route(data: TextLogItemData): String = buildString {
        append(ROUTE_BASE).append('?')
        append(ARG_TAG).append('=').append(Uri.encode(VALUE_PREFIX + data.tag))
        append('&').append(ARG_MESSAGE).append('=').append(Uri.encode(VALUE_PREFIX + data.text))
        append('&').append(ARG_TIMESTAMP).append('=').append(data.timePresent.timestamp)
        data.meta?.let {
            append('&').append(ARG_META).append('=').append(Uri.encode(VALUE_PREFIX + it.toString()))
        }
    }

    fun read(entry: NavBackStackEntry): Values {
        val args = requireNotNull(entry.arguments) { "logDetail destination opened without arguments" }
        return Values(
            tag = args.getString(ARG_TAG).orEmpty().removePrefix(VALUE_PREFIX),
            message = args.getString(ARG_MESSAGE).orEmpty().removePrefix(VALUE_PREFIX),
            timestamp = args.getLong(ARG_TIMESTAMP),
            meta = args.getString(ARG_META)?.removePrefix(VALUE_PREFIX),
        )
    }

    data class Values(val tag: String, val message: String, val timestamp: Long, val meta: String?)
}
```

Notes for the implementer:
- Navigation decodes query values with `Uri.decode`, so `Uri.encode` here round-trips `& ? / % # +`, newlines and non-ASCII.
- Every string value is prefixed with `VALUE_PREFIX` and stripped in `read()`: navigation 2.7.7's `StringType` parses the literal `"null"` into a real null (crashing non-nullable args), and a value equal to its own `{placeholder}` is dropped. Found in code review.
- `timestamp` has no default on purpose: `route()` always sets it (spec: missing = programming error).
- `data.meta` is a Gson `JsonObject`; `toString()` matches today's `putExtra(EXTRA_META, data.meta?.toString())`.

- [ ] **Step 2: Compile check** — run the build command. Expected: exit 0.

---

### Task 3: Shared copy helper

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/LogItemDetailCopy.kt`

- [ ] **Step 1: Create the file** (logic moved verbatim from the old `LogItemDetailActivity.copyLogDetail()`)

```kotlin
package com.khosravi.devin.present.present

import android.content.Context
import android.widget.Toast
import com.khosravi.devin.present.R
import com.khosravi.devin.present.setClipboardSafe

/** Copies a log's detail as plain text and confirms with a toast. Shared by both detail hosts. */
fun Context.copyLogDetail(tag: String, timeText: String, message: String, meta: String?) {
    val content = buildString {
        appendLine("Tag: $tag")
        appendLine("Time: $timeText")
        appendLine("Message: $message")
        meta?.let { appendLine("Meta: $it") }
    }
    if (setClipboardSafe(content)) {
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
}
```

- [ ] **Step 2: Compile check** — run the build command. Expected: exit 0.

---

### Task 4: `LogItemDetailScreen` composable

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/LogItemDetailScreen.kt`

Layout reference (old XML `activity_log_item_detail.xml`): 16dp page padding; Tag/Time rows with the label column at 28% width; Message and Meta are label-then-value blocks with 16dp (`spacing.medium`) gap before the label and 8dp (`spacing.small`) before the value; labels bold; every value has an extra 16dp start padding (from `AppTheme.TextAppearance.Value`); text is 14sp; values are selectable.

- [ ] **Step 1: Create the file**

```kotlin
package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.khosravi.devin.present.R
import com.khosravi.devin.present.uikit.theme.DevinTheme
import com.khosravi.devin.present.uikit.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogItemDetailScreen(
    tag: String,
    message: String,
    timeText: String,
    meta: String?,
    onCopy: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onCopy) {
                        Icon(
                            painterResource(R.drawable.baseline_content_copy_24),
                            contentDescription = stringResource(R.string.menu_log_detail_copy)
                        )
                    }
                }
            )
        }
    ) { padding ->
        SelectionContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(MaterialTheme.spacing.medium)
            ) {
                InlineField(label = stringResource(R.string.field_tag), value = tag)
                InlineField(
                    label = stringResource(R.string.field_time),
                    value = timeText,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.small)
                )
                BlockField(label = stringResource(R.string.field_message), value = message)
                if (meta != null) {
                    BlockField(label = stringResource(R.string.field_meta), value = meta)
                }
            }
        }
    }
}

@Composable
private fun InlineField(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(LABEL_WIDTH_FRACTION)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium)
        )
    }
}

@Composable
private fun BlockField(label: String, value: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = MaterialTheme.spacing.medium)
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small)
    )
}

/** Matches the old XML guideline at 28% of the content width. */
private const val LABEL_WIDTH_FRACTION = 0.28f

@Preview
@Composable
private fun LogItemDetailScreenPreview() {
    DevinTheme {
        LogItemDetailScreen(
            tag = "MyTag",
            message = "A quite long log message that describes what happened in the app.",
            timeText = "11:52:49.123",
            meta = "{\"userId\":42}",
            onCopy = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun LogItemDetailScreenNoMetaPreview() {
    DevinTheme {
        LogItemDetailScreen(
            tag = "MyTag",
            message = "No meta on this one.",
            timeText = "11:52:49.123",
            meta = null,
            onCopy = {},
            onBack = {},
        )
    }
}
```

Notes for the implementer:
- `BlockField` emits two siblings into the caller's `Column`; it's only called from inside the `Column`, so no wrapper is needed.
- Do **not** add `import androidx.compose.foundation.layout.weight` anywhere (known shadowing bug on Foundation 1.5.2).
- `Icons.Default.ArrowBack` is in `material-icons-core`; `Icons.AutoMirrored` does not exist in this Compose version.
- `bodyMedium` in Material3 1.1.2 is 14sp, matching `android:TextAppearance.Small`.

- [ ] **Step 2: Compile check** — run the build command. Expected: exit 0.

---

### Task 5: Rewrite `LogItemDetailActivity` as the bridge host

**Files:**
- Rewrite: `present-app/src/main/java/com/khosravi/devin/present/present/LogItemDetailActivity.kt`
- Delete: `present-app/src/main/res/layout/activity_log_item_detail.xml`
- Delete: `present-app/src/main/res/menu/log_item_detail_menu.xml`

- [ ] **Step 1: Replace the whole file with**

```kotlin
package com.khosravi.devin.present.present

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.date.getFormattedWithMillis
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.getLongExtraOrFail
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.uikit.theme.DevinTheme
import javax.inject.Inject

/**
 * Bridge host for [LogItemDetailScreen], used only by [ImportLogActivity]. [LogActivity] shows the
 * same screen as a navigation destination instead. Delete when ImportLogActivity is migrated.
 */
class LogItemDetailActivity : BaseActivity() {

    @Inject
    lateinit var calendar: CalendarProxy

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)

        val tag = intent.getStringExtra(EXTRA_TAG).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val timeText = calendar.getFormattedWithMillis(TimePresent(intent.getLongExtraOrFail(EXTRA_TIMESTAMP)))
        val meta = intent.getStringExtra(EXTRA_META)

        setContent {
            DevinTheme {
                LogItemDetailScreen(
                    tag = tag,
                    message = message,
                    timeText = timeText,
                    meta = meta,
                    onCopy = { copyLogDetail(tag, timeText, message, meta) },
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TAG = "tag"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_TIMESTAMP = "timestamp"
        private const val EXTRA_META = "meta"

        fun startActivity(context: Context, data: TextLogItemData) {
            val intent = Intent(context, LogItemDetailActivity::class.java).apply {
                putExtra(EXTRA_TAG, data.tag)
                putExtra(EXTRA_MESSAGE, data.text)
                putExtra(EXTRA_TIMESTAMP, data.timePresent.timestamp)
                putExtra(EXTRA_META, data.meta?.toString())
            }
            context.startActivity(intent)
        }
    }
}
```

- [ ] **Step 2: Delete the XML**

```bash
git rm -q present-app/src/main/res/layout/activity_log_item_detail.xml present-app/src/main/res/menu/log_item_detail_menu.xml
```

- [ ] **Step 3: Confirm nothing else references them**

```bash
grep -rn "activity_log_item_detail\|ActivityLogItemDetailBinding\|log_item_detail_menu\|action_copy_log_detail" present-app/src
```

Expected: no output.

Keep (do not delete): the manifest entry, `AppComponent.inject(activity: LogItemDetailActivity)`, strings `log_detail_title` / `field_*` / `menu_log_detail_copy` / `copied`, `baseline_content_copy_24`, and the `AppTheme.TextAppearance.Label/Value` styles (still used by `item_http_detail_overview.xml`).

- [ ] **Step 4: Compile check** — run the build command. Expected: exit 0.

---

### Task 6: NavHost in `LogActivity`

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt` (imports at top; `setContent { … }` block in `onCreate`, ~lines 91–115)

- [ ] **Step 1: Add imports** (keep alphabetical order with the existing ones)

```kotlin
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.date.getFormattedWithMillis
```

- [ ] **Step 2: Replace the whole `setContent { … }` block in `onCreate` with**

```kotlin
        setContent {
            DevinTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = ROUTE_LOGS) {
                    composable(ROUTE_LOGS) {
                        val items = remember(logRows, searchRow) { buildLogItems() }
                        LogScreen(
                            calendar = calendar,
                            filters = filterList,
                            selectedFilterId = selectedFilterId,
                            isFilterRowEnabled = isFilterRowEnabled,
                            logItems = items,
                            isLoading = isLoading,
                            onSelectFilter = ::selectNewFilter,
                            onFilterAction = ::onFilterAction,
                            onSearchTextChange = { searchInput.tryEmit(it) },
                            onTextLogClick = { navController.navigate(LogDetailArgs.route(it)) },
                            onHttpLogClick = { HttpLogDetailActivity.startActivity(this@LogActivity, it.logId) },
                            onLoadMore = { pagination.maybeLoadMore(::loadMoreItems) },
                            onRefresh = ::refreshLogsAndFilters,
                            onClearLogs = { viewModel.clearLogs() },
                            onClearFilters = { viewModel.clearCustomFilters() },
                            onCreateFilter = ::createFilter,
                            onExport = ::showExportDialog,
                            onToggleTheme = { viewModel.toggleTheme() },
                            onOpenSettings = { startActivity(SettingsActivity.intent(this@LogActivity)) },
                        )
                    }
                    composable(LogDetailArgs.ROUTE_PATTERN, arguments = LogDetailArgs.arguments) { entry ->
                        val args = remember(entry) { LogDetailArgs.read(entry) }
                        val timeText = remember(args.timestamp) {
                            calendar.getFormattedWithMillis(TimePresent(args.timestamp))
                        }
                        LogItemDetailScreen(
                            tag = args.tag,
                            message = args.message,
                            timeText = timeText,
                            meta = args.meta,
                            onCopy = { copyLogDetail(args.tag, timeText, args.message, args.meta) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
```

Why `this@LogActivity`: inside `composable { }` the lambda receiver is `AnimatedContentScope`, so a bare `this` no longer means the Activity. Every former bare `this` in the `LogScreen` call must be `this@LogActivity`.

- [ ] **Step 3: Add the route constant** to the existing `companion object` of `LogActivity`:

```kotlin
        private const val ROUTE_LOGS = "logs"
```

- [ ] **Step 4: Confirm `LogActivity` no longer opens the bridge**

```bash
grep -n "LogItemDetailActivity" present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt
```

Expected: no output. (`ImportLogActivity.kt` must still reference it — leave that file untouched.)

- [ ] **Step 5: Compile check** — run the build command. Expected: exit 0.

---

### Task 7: Verify on the emulator

**Files:** none changed (fix-ups only if a check fails).

- [ ] **Step 1: Install**

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:installDebug -q
```

Use `sample-app` (or an existing client) to have logs, including one with meta and one whose message contains `& ? / % # +`, a newline and an emoji. Prefer `adb shell uiautomator dump` + parsed bounds for tap targets (screenshot pixel math has been unreliable on this project).

- [ ] **Step 2: Run the checks** — each must pass:

1. Tap a text log in `LogActivity` → detail page shows tag, time with milliseconds, message. Tap copy → "Copied" toast; `adb shell cmd clipboard get-primary-clip` (or paste into the search field) shows the Tag/Time/Message[/Meta] text.
2. Back arrow, and separately system back, return to the list **at the same scroll position**, with the same filter selected and the search text still present (type into search, wait >1s, then open a log and come back).
3. A log with meta shows the Meta section; one without meta has no Meta label.
4. The special-character message renders exactly as logged (no `%26`, `+` turned into space, truncated text, etc.).
5. On the detail page: toggle dark mode (`adb shell cmd uimode night yes` / `no`) and rotate → page stays open and populated.
6. Bridge Activity via `ImportLogActivity`. It has **no UI entry point** (the import menu item was already commented out before this work) and is `exported="false"`, so launch it from a rooted adb shell with an exported log file:
   ```bash
   adb root
   adb push exported-logs.json /data/local/tmp/exported-logs.json
   adb shell am start -n com.khosravi.devin.present/.present.ImportLogActivity --eu key_data file:///data/local/tmp/exported-logs.json
   ```
   (Check the installed debug package name with `adb shell pm list packages | grep devin` and adjust if it differs from the namespace; produce `exported-logs.json` with the app's own Export action.) Tap a text log → bridge page shows the same UI; its back arrow closes it. If the emulator image can't `adb root` or the file can't be read, report check 6 as **compile-verified only** — do not add an entry point to make it reachable.
7. On the `logs` destination, system back still exits `LogActivity` as before.
8. A text log whose message is literally `null` opens without a crash and shows `null`.
9. Size ceiling: a ~100k-character non-ASCII (Persian/emoji) message → open detail, then press Home (forces saved-state) and return. Must not crash with `TransactionTooLargeException`. If it does, STOP and report — the fix (truncate vs. fall back to the bridge Activity) is a user decision.

- [ ] **Step 3: Report** results per check with screenshots for 1, 3 and 6. Do not commit — wait for the user.
