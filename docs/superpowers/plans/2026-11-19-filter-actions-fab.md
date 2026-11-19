# Filter Actions FAB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A FAB on the presenter's log screen that opens the selected filter's actions (same as the chip long-press menu) plus "Create filter".

**Architecture:** Extract the chip menu's items into one shared `filterMenuEntries(...)` function used by both the chip `DropdownMenu` and a new `FilterActionsFab` composable. `LogScreen` hosts the FAB in `Scaffold.floatingActionButton`, owns its expanded state, hides it on scroll down / in selection mode / while the filter row is disabled, and draws a scrim over the content while it is expanded.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 `1.4.0` (Compose BOM `2025.12.01`), `material-icons-core`.

**Spec:** `docs/superpowers/specs/2026-11-19-filter-actions-fab-design.md`

## Global Constraints

- `present-app` only. No dependency changes; stable material3 1.4.0 APIs only (`FloatingActionButtonMenu` does not exist in it; do not opt into `ExperimentalMaterial3ExpressiveApi`).
- Branch `filter-actions-fab` (from `presenter-release-4.11.0`). **Do not commit** and **do not add or change unit tests** (AGENTS.md); the user commits.
- Gradle needs JDK 17: prefix commands with `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`.
- Reuse `FilterChipAction`; `LogActivity.onFilterAction` stays unchanged.
- Chip long-press menu: same items, order, labels and behaviour as today.
- Comments follow the codebase style: short, explain *why*.

## Review Focus

1. Ids are not unique (a custom filter may be titled like a tag); the selected filter is `filters.find { it.id == selectedFilterId }`, same as `LogActivity.optCurrentFilterItem()`. Not found → treat as "All" (Create filter only).
2. Selecting another chip, refreshing (filter row disabled), or entering selection mode while the FAB menu is open must collapse it; the scrim must never stay on screen with the FAB hidden.
3. Back press while expanded collapses the menu only; it must not also close tag search or leave the screen.
4. Remove on the selected custom filter resets selection to "All"; afterwards the FAB must show only Create filter (no stale entries for the removed filter).
5. The last log row must be scrollable above the FAB (bottom content padding), and the FAB reappears on any upward scroll or at the top.

---

### Task 1: Shared filter menu entries

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/FilterMenuEntries.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/FilterChipsRow.kt` (the `DropdownMenu` block at the end of `FilterChipView`)

**Interfaces:**
- Produces:
  ```kotlin
  data class FilterMenuEntry(@StringRes val label: Int, val onClick: () -> Unit)

  /** The selected/long-pressed filter's actions, in long-press-menu order. Empty for the index ("All") filter. */
  fun filterMenuEntries(
      filter: FilterItem,
      onAction: (FilterChipAction) -> Unit,
      onStartSelection: (TagFilterItem) -> Unit,
  ): List<FilterMenuEntry>
  ```

- [ ] **Step 1:** Implement `filterMenuEntries` with exactly today's items: Pin/Unpin (`menu_action_unpin` if `filter.ui.isPinned` else `menu_action_pin`) → `TogglePin(filter)`; for `TagFilterItem`: `menu_action_share_as_json` → `ShareAsJson`, `menu_action_select_multiple` → `onStartSelection(filter)`; for `CustomFilterItem`: `menu_action_edit` → `Edit`, `menu_action_remove` → `Remove(listOf(filter))`. Return `emptyList()` when `filter.isIndexFilterItem()`.
- [ ] **Step 2:** Replace the hand-written items in `FilterChipView`'s `DropdownMenu` with a loop over `filterMenuEntries(filter, onAction, onStartSelection)`, each a `DropdownMenuItem` that sets `menuOpen = false` then calls `entry.onClick()`.
- [ ] **Step 3:** Build: `./gradlew :present-app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

### Task 2: `FilterActionsFab` composable

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/FilterActionsFab.kt`
- Create: `present-app/src/main/res/drawable/ic_filter_list_24px.xml` (Material "filter_list" vector, 24dp, path `M10,18h4v-2h-4v2zM3,6v2h18V6H3zM6,13h12v-2H6v2z`, `android:tint="?attr/colorControlNormal"` like `ic_keep_24px.xml`)
- Modify: `present-app/src/main/res/values/strings.xml` — add `filter_actions_open` = "Filter actions", `filter_actions_close` = "Close filter actions"

**Interfaces:**
- Consumes: `FilterMenuEntry` (Task 1).
- Produces:
  ```kotlin
  @Composable
  fun FilterActionsFab(
      entries: List<FilterMenuEntry>,
      expanded: Boolean,
      onExpandedChange: (Boolean) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

- [ ] **Step 1:** Implement: a `Column(horizontalAlignment = Alignment.End)`; above the FAB, `AnimatedVisibility(expanded)` (fade + expand vertically from bottom) holding one item per entry, top-to-bottom in entry order, each an `ExtendedFloatingActionButton`-style pill (`SmallFloatingActionButton` is icon-only, so use `ExtendedFloatingActionButton(onClick, text = { Text(label) }, icon = {})` or a `Surface` pill with `Text`; secondary-container colours), spaced `MaterialTheme.spacing.small`. Clicking an item calls `onExpandedChange(false)` then `entry.onClick()`. Below them the main `FloatingActionButton` toggling `onExpandedChange(!expanded)`, icon `ic_filter_list_24px` when collapsed / `Icons.Default.Close` when expanded, content descriptions `filter_actions_open` / `filter_actions_close`. `BackHandler(enabled = expanded) { onExpandedChange(false) }`.
- [ ] **Step 2:** Build: `./gradlew :present-app:compileDebugKotlin` → `BUILD SUCCESSFUL`.

### Task 3: Wire the FAB into `LogScreen`

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogScreen.kt` (`LogScreen`: `Scaffold` call ~line 158, content `Box` ~214, `LazyColumn` ~262)

**Interfaces:**
- Consumes: `filterMenuEntries`, `FilterMenuEntry` (Task 1); `FilterActionsFab` (Task 2). `LogScreen`'s parameters are unchanged.

- [ ] **Step 1: State.** In `LogScreen`: `var fabExpanded by rememberSaveable { mutableStateOf(false) }`; `var fabScrolledAway by remember { mutableStateOf(false) }`; `val selectedFilter = filters.find { it.id == selectedFilterId }`; `val fabVisible = selectedTags == null && isFilterRowEnabled && !fabScrolledAway`. Collapse when the target changes or the FAB hides: `LaunchedEffect(selectedFilterId) { fabExpanded = false }` and `LaunchedEffect(fabVisible) { if (!fabVisible) fabExpanded = false }`. (The first also runs on entry, which is fine: it only collapses.)
- [ ] **Step 2: Entries.** `fabEntries = (selectedFilter?.let { filterMenuEntries(it, onFilterAction, onStartSelection = { tag -> selectionIds = arrayListOf(tag.id) }) } ?: emptyList()) + FilterMenuEntry(R.string.menu_create_filter) { onFilterAction(FilterChipAction.CreateFilter(tags)) }` where `tags = listOfNotNull((selectedFilter as? TagFilterItem)?.tagValue)`. Use the same `onStartSelection` lambda the `FilterChipsRow` call uses (extract it to a local `val` and pass it to both).
- [ ] **Step 3: Scaffold slot.** `floatingActionButton = { AnimatedVisibility(fabVisible, enter = slide-in from bottom + fade, exit = slide-out to bottom + fade) { FilterActionsFab(fabEntries, fabExpanded, { fabExpanded = it }) } }`.
- [ ] **Step 4: Scroll hide/show.** On the `Box` wrapping the `LazyColumn`, add `Modifier.nestedScroll(connection)` with a remembered `NestedScrollConnection` whose `onPostScroll`/`onPreScroll` sets `fabScrolledAway = true` when `available.y < -1f` (finger moving up = scrolling down the list) and `false` when `available.y > 1f`. Also set `fabScrolledAway = false` in the existing at-top `snapshotFlow` collector when at top. Don't consume any scroll (return `Offset.Zero`), so pull-to-refresh and the list are unaffected.
- [ ] **Step 5: Last rows reachable.** `LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), ...)` with a comment that it keeps the last row scrollable above the FAB.
- [ ] **Step 6: Scrim.** As the last child of the content `Box` (after the pull indicator), `if (fabExpanded)` a full-size `Box` with `background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))` and `clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fabExpanded = false }`. (Scaffold draws the FAB above content, so the menu stays tappable.)
- [ ] **Step 7:** Build and install: `./gradlew :present-app:installDebug` → `BUILD SUCCESSFUL`, `Installed on 1 device`.

### Task 4: Device check

- [ ] **Step 1:** Launch the app on the connected device (`adb shell monkey -p <present-app debug applicationId> 1`, applicationId from `present-app/build.gradle.kts`), open a client's logs, and check with screenshots (`adb exec-out screencap -p > /tmp/x.png`):
  - "All" selected → FAB opens to only "Create custom filter"; it opens the create-filter screen with no tags picked.
  - A tag chip selected → Pin/Unpin, Share as json, Select multiple, Create custom filter; Create preselects that tag; Select multiple enters selection mode and the FAB disappears.
  - A custom filter selected → Pin/Unpin, Edit, Remove, Create custom filter; Remove returns to "All".
  - Scrim tap and Back collapse the menu; scrolling down hides the FAB, scrolling up shows it; the last row scrolls clear of the FAB.
  - Long-press on a chip still shows the same menu as before.
- [ ] **Step 2:** Report what was checked and anything that could not be (e.g. no custom filter on the device).
