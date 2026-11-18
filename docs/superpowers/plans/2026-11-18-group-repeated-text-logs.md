# Group Repeated Text Logs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the presenter, fold back-to-back text logs that have the same tag and message into one expandable row with a `×N` badge.

**Architecture:** Grouping is a pure list transform that runs in the UI layer, on the **whole accumulated list**. `LogActivity` already appends every page to `logRows` (`LogActivity.kt:199`), so a run of repeats that spans a page boundary groups correctly with no paging logic. A second pure transform turns expanded groups into a header followed by child rows, so both screens keep a flat `LazyColumn`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), SharedPreferences (`AppPref`), JUnit 4.

**Spec:** GitHub issue nasserkhosravi/devin-proj#181 (milestone "presnter 4.11.0").

## Global Constraints

- Branching: create the feature branch `group-repeated-logs` from `presenter-release-4.11.0` and merge it back there, never into `main` (AGENTS.md).
- Commits and tests: per AGENTS.md, **don't commit, and don't add or change tests, until the user says so**. Test and commit steps below run only after that go-ahead.
- Gradle needs JDK 17: prefix commands with `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`.
- Grouping rule: only `TextLogItemData`; same `tag` **and** same `text`; adjacent only. Any other item (HTTP, image, `DateLogItemData`, `SessionStartLogItemData`) ends a group. Time, `meta` and `logLevel` may differ. A run of 1 stays a plain `TextLogItemData`.
- Lists are newest first, so a group's `list.first()` is its newest copy.
- Changes stay inside `present-app`.

## Review Focus

1. **Repeats split across two pages** must show as one group. Test: grouping `page1 + page2`, where the run straddles the join, gives one group (Task 1).
2. **Same message, different tag** must not group (Task 1).
3. **A session start between copies**: after `sessionStartsFirst()` reorders rows, the session start row must split the run. Test: a `SessionStartLogItemData` between copies gives two groups (Task 1).
4. **Pull to refresh or new logs while a group is expanded**: the group key can change, so the group collapses. That's acceptable, but there must be no crash and no orphan child rows. Test: an expanded key that matches no group adds no children (Task 2).
5. **Mixed levels in one group**: the header shows the most severe one. Test: `logLevel` is the max of the copies' levels (Task 1).

---

### Task 1: Grouping rule and group model

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/log/ReplicatedTextLogItemData.kt`
- Modify (rewrite body): `present-app/src/main/java/com/khosravi/devin/present/present/logic/CountingReplicatedTextLogItemDataOperation.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/ReaderViewModel.kt:131-132`: delete the commented-out call and its TODO.
- Test: `present-app/src/test/java/com/khosravi/devin/present/present/logic/CountingReplicatedTextLogItemDataOperationTest.kt` (extend; the existing tests all use tag `"tag"` and still hold).

**Interfaces:**
- Produces: `ReplicatedTextLogItemData(list: List<TextLogItemData>)` with `text`, `timePresent` (existing, newest copy), plus:
  - `tag: String` = `list.first().tag`
  - `logLevel: Int` = `list.maxOf { it.logLevel }` (android `Log` constants, so the max is the most severe)
  - `count: Int` = `list.size`
  - `groupKey: String` = `"$tag|$text|${list.first().timePresent.timestamp}"`
- Produces: `CountingReplicatedTextLogItemDataOperation(logs: List<LogItemData>).get(): List<LogItemData>` (same signature, new rule).

- [ ] **Step 1: Add the failing tests** (after go-ahead). The `text()` helper gets `tag` and `level` params with defaults `"tag"` and `0`:
  - `same text with different tags is not grouped`: `[text("a", tag="x"), text("a", tag="y")]` → 2 `TextLogItemData`.
  - `group level is the most severe copy`: copies at levels `Log.INFO`, `Log.ERROR`, `Log.DEBUG` → one group with `logLevel == Log.ERROR`, `count == 3`.
  - `session start splits a run`: `[text("a"), SessionStartLogItemData(...), text("a")]` → 3 items, none grouped.
  - `run across a page join is one group`: `run(*(page1 + page2))` where `page1` ends with `text("a")` twice and `page2` starts with `text("a")` → one group, `count == 3`.
- [ ] **Step 2: Run them, expect the tag test to FAIL**
  `./gradlew :present-app:testDebugUnitTest --tests "*CountingReplicatedTextLogItemDataOperationTest"`
- [ ] **Step 3: Implement.** Compare `tag` and `text` together, and replace the `IllegalStateException` branch with a plain flush and start of a new run. Add the `ReplicatedTextLogItemData` properties above. Remove the dead lines in `ReaderViewModel`.
- [ ] **Step 4: Run the tests, expect PASS.**
- [ ] **Step 5: Commit** (after go-ahead): `Group repeated text logs by tag and message`

### Task 2: Expanding groups into rows

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/log/RepeatedTextLogChildItemData.kt`
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logic/ExpandRepeatedGroups.kt`
- Test: `present-app/src/test/java/com/khosravi/devin/present/present/logic/ExpandRepeatedGroupsTest.kt`

**Interfaces:**
- Produces: `class RepeatedTextLogChildItemData(val groupKey: String, val log: TextLogItemData) : LogItemData`. Adding it to the sealed `LogItemData` makes the compiler flag every exhaustive `when`. For any branch outside Task 3's rows, handle it like `TextLogItemData` via `.log`.
- Produces: `fun List<LogItemData>.expandRepeatedGroups(expandedKeys: Set<String>): List<LogItemData>`. Every `ReplicatedTextLogItemData` whose `groupKey` is in `expandedKeys` is followed by one `RepeatedTextLogChildItemData` per copy, in order. Everything else passes through.

- [ ] **Step 1: Failing tests** (after go-ahead): `collapsed group adds no children`; `expanded group is followed by its copies in order` (asserts `.log` identity and `groupKey`); `unknown expanded key adds nothing` (Review Focus 4).
- [ ] **Step 2: Run, expect FAIL** (unresolved reference).
- [ ] **Step 3: Implement both files.**
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** (after go-ahead): `Expand repeated-log groups into child rows`

### Task 3: Group row UI on both screens

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logitem/RepeatedTextLogRow.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogScreen.kt` (`LogScreen`, `LogItemDataRow`)
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/ImportLogScreen.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt`, `ImportLogActivity.kt` (pass the flag; Task 4 supplies its value, so use `true` until then)
- Modify: `present-app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Task 1's `ReplicatedTextLogItemData`, Task 2's `expandRepeatedGroups` and `RepeatedTextLogChildItemData`.
- Produces:
  - `@Composable fun RepeatedTextLogRow(calendar: CalendarProxy, data: ReplicatedTextLogItemData, isExpanded: Boolean, ignoreTagChip: Boolean, onClick: () -> Unit, showDivider: Boolean = true)`
  - `@Composable fun RepeatedTextLogChildRow(calendar: CalendarProxy, data: RepeatedTextLogChildItemData, onClick: () -> Unit)`
  - `LogItemDataRow` gains `expandedGroupKeys: Set<String>` and `onGroupClick: (ReplicatedTextLogItemData) -> Unit`.
  - `LogScreen(…, groupRepeatedLogs: Boolean, …)` and `ImportLogScreen(…, groupRepeatedLogs: Boolean, …)`.

Look (option A from the mockups):
- **Header:** same layout as `TextLogRow`, using the group's `logLevel` style, the newest copy's time, the tag chip (respecting `ignoreTagChip`) and the message preview. On the right, a filled `×N` pill (`primary` / `onPrimary`, `labelSmall`) and a chevron that points down when collapsed and up when expanded.
- **Child:** compact, start padding aligned under the header's text column (icon size + `spacing.large`), `bodySmall` time (`getFormattedWithMillis`) followed by a one-line, ellipsized message, `clickable`.
- **Content description:** the pill reads "Repeated %d times" (`R.string.log_repeated_count`). The chevron reads "Expand" or "Collapse" (`R.string.log_group_expand` / `log_group_collapse`).

- [ ] **Step 1: Add both composables and the three strings.**
- [ ] **Step 2: Wire `LogItemDataRow`.** `ReplicatedTextLogItemData` → `RepeatedTextLogRow` (click → `onGroupClick`). `RepeatedTextLogChildItemData` → `RepeatedTextLogChildRow` (click → `onTextLogClick(data.log)`). Replace the current `Unit` branch.
- [ ] **Step 3: `LogScreen`.** Keep `var expandedKeys by rememberSaveable { mutableStateOf(ArrayList<String>()) }`. The pipeline inside the existing `remember`: `filterIsInstance<Row>().sessionStartsFirst()` → map to data → `if (groupRepeatedLogs) CountingReplicatedTextLogItemDataOperation(it).get() else it` → `expandRepeatedGroups(expandedKeys.toSet())` → map back to `Row`. `onGroupClick` toggles the key. Add `groupRepeatedLogs` and `expandedKeys` to the `remember` keys.
- [ ] **Step 4: `ImportLogScreen`.** Same pipeline after `filterByText(query)`, with its own `rememberSaveable` expanded keys.
- [ ] **Step 5: Build.** `./gradlew :present-app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Check by hand** with `sample-app` writing a burst of the same log, plus one log of another tag in the middle: one `×N` row on each side of the other log. Tapping expands it, tapping a child opens the detail, and back returns with the group still expanded. Rotate once: still expanded. Scroll until the next page loads inside a run: the count grows and no second row appears.
- [ ] **Step 7: Commit** (after go-ahead): `Show repeated text logs as an expandable row`

### Task 4: Setting to turn grouping off

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/data/AppPref.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/settings/SettingsScreen.kt`, `SettingsActivity.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt`, `ImportLogActivity.kt`
- Modify: `present-app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `AppPref.groupRepeatedLogs: Boolean` (get/set), key `"group_repeated_logs"`, default `true`.
- Produces: `SettingsScreen(…, groupRepeatedLogs: Boolean, onGroupRepeatedLogsChange: (Boolean) -> Unit, …)`.

- [ ] **Step 1: Add the pref.**
- [ ] **Step 2: Settings UI.** A new section above Storage: header "Log list" (`settings_section_log_list`), a `SwitchRow` "Group repeated logs" (`settings_group_repeated_logs`), and the summary "Folds back-to-back copies of the same log into one row" (`settings_group_repeated_logs_summary`). Wire it through `SettingsActivity` the way `retentionEnabled` is.
- [ ] **Step 3: Read it in the screens.** In `LogActivity`, keep `groupRepeatedLogs` in `mutableStateOf` and refresh it in `onResume` (so it updates on return from Settings), via a `ReaderViewModel.isGroupRepeatedLogsEnabled()` that reads `appPref`. `ImportLogActivity` reads it the same way. Replace Task 3's placeholder `true`.
- [ ] **Step 4: Build and check by hand.** Toggle off → every copy shows as its own row. Toggle on → groups come back.
- [ ] **Step 5: Commit** (after go-ahead): `Add setting to group repeated logs`

### Release bookkeeping (not part of this feature branch)

When the milestone is ready, the `presenter-release-4.11.0` branch's own commit adds the `## 4.11.0` CHANGELOG bullet "Group repeated logs into an expandable row", bumps `versionName`/`versionCode`, and runs `scripts/check-presenter-release.sh`.
