# Pull To Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pulling down at the top of the log list and the client list runs the same refresh as the top-bar button, with a spinner while it runs.

**Architecture:** Use the experimental `androidx.compose.material` pull-refresh API (`Modifier.pullRefresh`, `rememberPullRefreshState`, `PullRefreshIndicator`), because the current Compose BOM (2023.09.02) has no pull-to-refresh container in Material 3. Each activity owns an `isRefreshing` flag: set when a refresh starts, cleared when its result arrives. Each screen takes `isRefreshing: Boolean` and wires the gesture to its existing `onRefresh`.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2023.09.02, Material 3 plus the Material 2 artifact for pull-refresh).

**Spec:** GitHub issue nasserkhosravi/devin-proj#175 (milestone "presnter 4.11.0").

## Global Constraints

- Branching: feature branch `pull-to-refresh` was cut from `link-open-copy-menu` (stacked on PRs #184 and #185, which edit `LogScreen.kt` and `LogActivity.kt`). It merges into `presenter-release-4.11.0`, never `main` (AGENTS.md).
- Commits and tests: per AGENTS.md, **don't commit, and don't add or change tests, until the user says so**. This plan adds no new unit tests (there is no pure logic: it is gesture and state wiring), so only the commit step waits for the go-ahead.
- Gradle needs JDK 17: prefix commands with `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`.
- The top-bar refresh buttons stay. The log list keeps its "refreshed" / "empty filter" toast.
- The Compose BOM is **not** upgraded. The Material 2 artifact is added only for pull-refresh; follow-up #183 switches to the Material 3 container later.
- Changes stay inside `present-app` plus `gradle/libs.versions.toml`.

## Review Focus

1. **Spinner never hides** if a refresh can't finish (log list with no current filter, client fetch that needs the notification permission first). Both must clear `isRefreshing` (Task 1 Step 4, Task 2 Step 3).
2. **Pull in tag selection mode** on the log list is disabled, so a drag in selection mode can't refresh (Task 1 Step 3).
3. **Empty list** (no logs, no clients) can still be pulled (Tasks 1 and 2, by hand).
4. **Pull while a refresh is running** starts no second refresh (state's `refreshing` flag blocks it; Task 1 Step 2).
5. **Rotation mid-refresh** keeps the state: the flag lives in the activity, so it survives; no crash (by hand).

---

### Task 1: Log list

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `present-app/build.gradle.kts`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogScreen.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt`

**Interfaces:**
- Produces: `LogScreen(…, isRefreshing: Boolean, …)` (new parameter right after `isLoading`).
- Produces: a catalog entry `compose-material = { module = "androidx.compose.material:material" }` (version from the BOM) and `implementation(libs.compose.material)`.

- [ ] **Step 1: Add the dependency** in the two files above. Confirm it resolves: `./gradlew :present-app:dependencies --configuration debugRuntimeClasspath | grep "compose.material:material:"` shows a `1.5.x` version.
- [ ] **Step 2: `LogScreen` UI.** Add `isRefreshing: Boolean`. Inside the `Box` that holds the list (around `LogScreen.kt:179`), create `rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefresh)`, apply `Modifier.pullRefresh(state, enabled = selectedTags == null)` to that `Box`, and place `PullRefreshIndicator(isRefreshing, state, Modifier.align(Alignment.TopCenter))` as its last child. `@OptIn(ExperimentalMaterialApi::class)` on `LogScreen`.
- [ ] **Step 3: `LogActivity` state.** Add `private var isRefreshing by mutableStateOf(false)`, pass `isRefreshing = isRefreshing` to `LogScreen`. In `refreshLogsAndFilters()` set it to `true` only inside the `optCurrentFilterItem()?.let { … }` block. In `onUiStateFlowResult`, in the `CALLBACK_ID_REFRESH` branch, set it to `false`.
- [ ] **Step 4: Failure path.** The refresh also reports through `ReaderViewModel.refreshLogsAndFilters`; check that every path of that flow ends in `onUiStateFlowResult` with `CALLBACK_ID_REFRESH` (success, empty result). If one can end without it, clear `isRefreshing` there too, and say which in the commit message.
- [ ] **Step 5: Build.** `./gradlew :present-app:assembleDebug` → BUILD SUCCESSFUL, and `./gradlew :present-app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 6: Check by hand** (connected device): pull down on the log list: spinner shows, the logs reload, the "refreshed" toast shows, the spinner hides. Pull on an empty list (filter with no logs): it still works. Enter "Select multiple" and pull: nothing happens. Rotate during a pull: no crash. The top-bar button still works.

### Task 2: Client list

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/StarterScreen.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/StarterActivity.kt`

**Interfaces:**
- Consumes: Task 1's dependency.
- Produces: `StarterScreen(…, isRefreshing: Boolean, …)` (new parameter right before `onRefresh`).

- [ ] **Step 1: `StarterScreen` UI.** Add `isRefreshing`. Wrap the client `LazyColumn` (`StarterScreen.kt:~95`) in a `Box(Modifier.weight(1f).pullRefresh(state))` with the `PullRefreshIndicator` as its last child, the `LazyColumn` filling it. Same state wiring as Task 1: `rememberPullRefreshState(isRefreshing, onRefresh)`, `@OptIn(ExperimentalMaterialApi::class)`. The banner, headline and footer stay outside the `Box`.
- [ ] **Step 2: `StarterActivity` state.** Add `private var isRefreshing by mutableStateOf(false)` and pass it. `refreshClients()` sets it to `true` then calls `launchGettingClientList()`.
- [ ] **Step 3: Clear it.** In `launchGettingClientList()`'s `collect`, set `isRefreshing = false` after the permission check (whether or not `onClientListFetchResult` ran), so a result that waits for the notification permission can't leave the spinner up.
- [ ] **Step 4: Build.** `./gradlew :present-app:assembleDebug` and `:present-app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 5: Check by hand:** pull down on the client list: "loading" shows, the list reloads, the spinner hides. With no clients installed (or the list empty), pulling still works. The top-bar button still works.
- [ ] **Step 6: Commit** (after go-ahead), both tasks together: `Add pull to refresh to the log and client lists`.
