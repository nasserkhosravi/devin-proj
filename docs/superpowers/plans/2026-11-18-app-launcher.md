# App Launcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Launcher page in the presenter opens the selected client app by deeplink, optionally with one String extra holding a JSON object, and remembers each launch per client so it can be relaunched, edited or deleted.

**Architecture:** A new `launcher` package in `present-app`. Pure rules (input validation, JSON identity, deeplink patterns from manifest filters) are plain functions with JVM unit tests. `LaunchRepository` keeps saved launches in the presenter's own SharedPreferences (Robolectric tests). `DeeplinkScanner` reads the client's binary manifest and feeds the pure pattern builder. `LauncherViewModel` + `LauncherScreen` form a new destination in `LogActivity`'s NavHost, opened from the log list's overflow menu.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3, BOM 2023.09.02), Navigation Compose 2.7.7, SharedPreferences + `org.json`, JUnit 4 + Robolectric 4.12 (`sdk=34`).

**Spec:** GitHub issue nasserkhosravi/devin-proj#137, including its "Spec review decisions" section (milestone "presnter 4.11.0").

## Global Constraints

- Branching: feature branch `app-launcher` was cut from `presenter-release-4.11.0` (after #184–#187 merged; #188 for #155 is still open and also touches `LogActivity.kt`, so expect a small merge). It merges back into the release branch, never `main` (AGENTS.md).
- Commits and tests: per AGENTS.md, **don't commit, and don't add or change tests, until the user says so**. Test and commit steps run only after that go-ahead.
- Gradle needs JDK 17: prefix commands with `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`.
- `present-app` only. No `devin-write` change, no new permission (the existing `<queries>` for `MAIN`/`LAUNCHER` gives visibility), no `QUERY_ALL_PACKAGES`.
- Storage: SharedPreferences file `launcher_v1`, key = client `packageId`, value = JSON array of entries, newest first, at most **50** per client.
- The extra is always a **String** extra with the JSON text exactly as typed.
- Plain unit tests that touch `org.json` work because `present-app` already has a real `org.json:json` `testImplementation` (AGENTS.md gotcha).
- `git add` only the files this plan names; `present-app/debug/` and `present-app/release/` hold untracked APKs that must never be committed.

## Review Focus

1. **Matching activity not exported** → `SecurityException`, not `ActivityNotFoundException`: shows the launch error and saves nothing (Task 4, `LaunchResult.Failure`).
2. **Scheme from a resource** (`android:scheme="@string/…"`) resolves to the real scheme (Task 3: the parser resolves references; by hand on a client that does this).
3. **Same JSON, different whitespace** counts as identical and doesn't duplicate (Task 2 test).
4. **Half-filled extra:** a value without a key can't be launched; a key without a value is ignored (Task 1 test).
5. **A big client manifest** (dozens of filters) doesn't block the UI: the scan runs on `Dispatchers.IO` (Task 4).

---

### Task 1: Input rules

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/launcher/LaunchInput.kt`
- Test: `present-app/src/test/java/com/khosravi/devin/present/launcher/LaunchInputTest.kt`

**Interfaces:**
- Produces: `data class LaunchInput(val deeplink: String, val extraKey: String?, val extraValue: String?)` — trimmed; blank key/value are `null`.
- Produces: `enum class LaunchInputError { MissingDeeplink, InvalidDeeplink, MissingKey, InvalidJson }`. (Ruling during the build: `MissingValue` was dropped. The key field is pre-filled, so a key without a value is ignored instead of blocking a deeplink-only launch.)
- Produces: `fun validateLaunchInput(deeplink: String, extraKey: String, extraValue: String): LaunchInputError?` — first failing rule in the order above; `null` when valid. A deeplink is valid when it matches `^[A-Za-z][A-Za-z0-9+.\-]*:` after trimming. `InvalidJson` when the value is not a JSON object (`JSONObject(text)` throws, or the text is an array/plain value).
- Produces: `fun LaunchInput.Companion.of(deeplink: String, extraKey: String, extraValue: String): LaunchInput` (trims, blank → `null`).
- Produces: `fun LaunchInput.identity(): String` — `deeplink + "\u0000" + (extraKey ?: "") + "\u0000" + (extraValue?.let { JSONObject(it).toString() } ?: "")`.

- [ ] **Step 1: Failing tests** (after go-ahead):
  - `blank deeplink is missing`: `validateLaunchInput("  ", "", "")` → `MissingDeeplink`.
  - `deeplink needs a scheme`: `"host/path"` → `InvalidDeeplink`; `"myapp://x"`, `"https://a.io"`, `"tel:123"` → `null`.
  - `a value needs a key, a key alone is ignored`: `("a://b", "", "{}")` → `MissingKey`; `("a://b", "k", "")` → `null` and `LaunchInput.of` drops the key; `("a://b", "", "")` → `null`.
  - `value must be a JSON object`: `"[1]"`, `"5"`, `"{bad"` → `InvalidJson`; `"{\"a\":1}"` → `null`.
  - `identity ignores JSON whitespace`: `LaunchInput.of("a://b","k","{ \"a\" : 1 }").identity() == LaunchInput.of("a://b","k","{\"a\":1}").identity()`, and differs when the key differs.
- [ ] **Step 2: Run, expect FAIL** (unresolved reference): `./gradlew :present-app:testDebugUnitTest --tests "*LaunchInputTest"`
- [ ] **Step 3: Implement** `LaunchInput.kt`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** (after go-ahead): `Add launcher input rules`

### Task 2: Saved launches

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/launcher/LaunchRepository.kt`
- Test: `present-app/src/test/java/com/khosravi/devin/present/launcher/LaunchRepositoryTest.kt` (Robolectric, like `data/FilterRepositoryTest.kt`)

**Interfaces:**
- Consumes: Task 1's `LaunchInput`, `identity()`.
- Produces: `data class LaunchEntry(val id: String, val input: LaunchInput, val lastLaunchedAt: Long)`.
- Produces: `class LaunchRepository @Inject constructor(appContext: Context)` with:
  - `fun list(clientId: String): List<LaunchEntry>` — newest first; unparsable entries skipped.
  - `fun recordLaunch(clientId: String, input: LaunchInput, editingId: String?, now: Long): LaunchEntry` — if `editingId` names an entry, that entry gets the new input; else an entry with the same `identity()` is reused; else a new one (`UUID`). Any other entry with the same identity as the result is removed. The result goes to the top with `lastLaunchedAt = now`; the list is cut to 50.
  - `fun delete(clientId: String, id: String): LaunchEntry?` (returns what was removed, for undo) and `fun restore(clientId: String, entry: LaunchEntry)` (puts it back by `lastLaunchedAt` order).
  - `fun lastExtraKey(clientId: String): String?` — the key of the newest entry that has one.

- [ ] **Step 1: Failing tests** (after go-ahead), `now` passed explicitly:
  - `new launches are listed newest first` (two inputs at `now` 1 and 2 → order 2, 1).
  - `an identical launch moves to the top without a duplicate` (A, B, then A again with different JSON whitespace → `[A, B]`, size 2, A's `lastLaunchedAt` updated).
  - `editing updates the entry in place` (A, B; `recordLaunch(…, input C, editingId = A.id, now = 3)` → list `[C with A's id, B]`).
  - `editing into an existing entry's values merges them` (A, B; edit A into B's input → one entry).
  - `the list is capped at 50` (51 launches → size 50, the oldest gone).
  - `delete and restore` (delete B returns B; restore puts it back in its place).
  - `lastExtraKey is the newest key` (`null` when no entry has one).
  - `clients are kept apart`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :present-app:testDebugUnitTest --tests "*LaunchRepositoryTest"`
- [ ] **Step 3: Implement.** One `JSONArray` string per client key in prefs `launcher_v1`; each entry `{id, deeplink, key, value, at}`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** (after go-ahead): `Add launcher storage`

### Task 3: Deeplink suggestions

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/launcher/DeeplinkPatterns.kt` (pure)
- Create: `present-app/src/main/java/com/khosravi/devin/present/launcher/DeeplinkScanner.kt` (Android)
- Test: `present-app/src/test/java/com/khosravi/devin/present/launcher/DeeplinkPatternsTest.kt`

**Interfaces:**
- Produces: `data class ManifestData(val scheme: String?, val host: String?, val port: String?, val path: String?, val pathPrefix: String?, val pathPattern: String?)` and `data class ManifestIntentFilter(val exported: Boolean, val actions: Set<String>, val categories: Set<String>, val data: List<ManifestData>)`.
- Produces: `fun deeplinkPatterns(filters: List<ManifestIntentFilter>): List<String>` — keeps exported filters with `android.intent.action.VIEW` + `android.intent.category.BROWSABLE` and at least one scheme; combines all `<data>` of a filter: every scheme × every host (with `:port` when given) × every path form (`path`, `pathPrefix*`, `pathPattern` raw; none → no path); no host → `scheme:`; with host → `scheme://host[:port][path]`. Result distinct and sorted.
- Produces: `class DeeplinkScanner @Inject constructor(appContext: Context) { fun scan(packageId: String): List<String> }` — opens `pm.getResourcesForApplication(packageId).assets.openXmlResourceParser("AndroidManifest.xml")`, walks `<activity>`/`<activity-alias>` → `<intent-filter>` → `<action>`/`<category>`/`<data>` (attributes in the `http://schemas.android.com/apk/res/android` namespace; a reference value is resolved with `resources.getString`), treats `exported` as true unless it is explicitly false, and returns `deeplinkPatterns(filters)`. Any exception → empty list.

- [ ] **Step 1: Failing tests** for `deeplinkPatterns` (after go-ahead):
  - `keeps only exported VIEW+BROWSABLE filters with a scheme` (a filter missing BROWSABLE, one with `exported = false`, one without data → none of them).
  - `combines schemes, hosts and paths`: schemes `myapp`, `https`; host `a.io`; `pathPrefix = "/ride"` → `https://a.io/ride*`, `myapp://a.io/ride*`.
  - `host with port and plain path`: `myapp://h:8080/p`.
  - `scheme only`: `tel:`.
  - `pathPattern is shown raw`: `myapp://h/.*/x`.
  - `duplicates are dropped and the list is sorted`.
- [ ] **Step 2: Run, expect FAIL**: `./gradlew :present-app:testDebugUnitTest --tests "*DeeplinkPatternsTest"`
- [ ] **Step 3: Implement** `DeeplinkPatterns.kt`, then `DeeplinkScanner.kt` (no unit test; checked by hand in Task 4).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** (after go-ahead): `Add deeplink suggestions from the client manifest`

### Task 4: Launcher page

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/launcher/AppLauncher.kt`
- Create: `present-app/src/main/java/com/khosravi/devin/present/launcher/LauncherViewModel.kt`
- Create: `present-app/src/main/java/com/khosravi/devin/present/launcher/LauncherScreen.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/di/ViewModelModule.kt`, `di/ViewModelFactory.kt` (inject `LaunchRepository`, `DeeplinkScanner`; create `LauncherViewModel`)
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogScreen.kt` (overflow item, `onOpenLauncher: () -> Unit`)
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt` (route `launcher`)
- Modify: `present-app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: `sealed interface LaunchResult { object Success; data class Failure(val reason: Reason) }` (`Reason` = `NoHandler`, `NotExported`) and `fun launchClient(context: Context, packageId: String, input: LaunchInput): LaunchResult` — `Intent(Intent.ACTION_VIEW, Uri.parse(input.deeplink)).setPackage(packageId)` plus `putExtra(key, value)` when both set; catches `ActivityNotFoundException` and `SecurityException` into `Failure`.
- Produces: `class LauncherViewModel(application, cacheRepo: CacheRepository, repository: LaunchRepository, scanner: DeeplinkScanner)` exposing a `StateFlow<LauncherUiState>`: `deeplink`, `extraKey`, `extraValue`, `inputError: LaunchInputError?`, `editingId: String?`, `entries: List<LaunchEntry>`, `suggestions: List<String>`, plus one-shot events for the snackbars (launch failed, deleted with undo). Actions: `onDeeplinkChange/onKeyChange/onValueChange`, `launch(context)`, `relaunch(context, entry)`, `edit(entry)`, `cancelEdit()`, `delete(entry)`, `undoDelete()`. On start it loads entries, pre-fills the key with `lastExtraKey`, and scans suggestions on `Dispatchers.IO`.
- Produces: `@Composable fun LauncherScreen(viewModel: LauncherViewModel, onBack: () -> Unit)`.
- Produces strings: `menu_launcher` ("Launcher"), `launcher_title` ("Launcher"), `launcher_deeplink`, `launcher_extra_key`, `launcher_extra_value`, `launcher_launch`, `launcher_editing` ("Editing…"), `launcher_relaunch`, `launcher_edit`, `launcher_delete`, `launcher_deleted` ("Launch removed"), `launcher_undo`, `launcher_failed_no_handler`, `launcher_failed_not_exported`, `launcher_saved_empty`, and one string per `LaunchInputError`.

- [ ] **Step 1: `AppLauncher.kt`.**
- [ ] **Step 2: `LauncherViewModel`** and its factory wiring. The client is `cacheRepo.getSelectedClientId()`; with none, the page shows nothing to launch.
- [ ] **Step 3: `LauncherScreen`:** `Scaffold` with a `TopAppBar` (back arrow, title) and its own `SnackbarHost`. Inputs: deeplink field with a suggestions dropdown filtered by the typed text (a `DropdownMenu` under the field is enough; pick fills the field), key field, multiline value field with the inline error, Launch button disabled while `inputError != null`. In editing mode an input chip "Editing…" with ✕ (`cancelEdit`). Below, the saved list, newest first: deeplink (one line), key, a 3-line JSON preview, and Relaunch / Edit / Delete buttons. Delete shows the undo snackbar.
- [ ] **Step 4: Wire it.** `LogScreen`'s overflow menu gets "Launcher" above Settings (`onOpenLauncher`). `LogActivity`'s NavHost gets `composable("launcher")` with `LauncherViewModel` from `viewModel(factory = vmFactory)` scoped to that entry, `onBack = navController::popBackStack`; navigation guarded by `isCurrentEntryResumed()` like the other routes.
- [ ] **Step 5: Build and unit tests.** `./gradlew :present-app:assembleDebug :present-app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 6: Check by hand** on the phone, with a client that declares deeplinks (the installed Snapp QA client, or a test activity in `sample-app` if the user OKs it): the menu item opens the page; suggestions list the client's links; a bad JSON shows the error and disables Launch; a valid launch opens the client and appears at the top of the list; the same launch again doesn't duplicate; Edit → change → Launch updates that entry; ✕ leaves editing; Delete + Undo; a deeplink nothing handles shows the error and saves nothing; rotate with text typed (fields kept by the view model); leave and come back (list kept).
- [ ] **Step 7: Commit** (after go-ahead): `Add an app launcher page to the presenter`
