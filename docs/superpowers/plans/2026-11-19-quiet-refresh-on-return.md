# Quiet Refresh On Return Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Model:** the user wants the implementation done with **Sonnet**. Dispatch every implementer subagent with `model: "sonnet"`. If you use executing-plans in one session instead, run that session on Sonnet.

**Goal:** When the user comes back to Devin, logs that match the selected filter and were written in the meantime are added at the top of the log list. There is no spinner and no toast, and the scroll position stays where it was. A "↑ N new logs" pill and a "N new logs above" divider show what is new.

**Architecture:** `LogActivity.onStart` (except the first start and a recreation) asks `ReaderViewModel.fetchNewLogs` for every log newer than the loaded rows' newest id (`_id > ?`, no page limit), for the selected filter and search text. The activity adds them in front of `logRows` with a pure merge function. `LazyColumn` rows get stable keys from the log id, so Compose keeps the first visible row in place. Two small activity states drive the hint: `newLogsCount` (pill) and `newLogsBoundaryId` (divider). The rest is pure functions in `present/logic/`, so it can be unit tested.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2023.09.02, foundation 1.5), coroutines.

**Spec:** GitHub issue nasserkhosravi/devin-proj#182 (milestone "presnter 4.11.0"). Mockups: https://claude.ai/artifact/QwxTF3fo6BHp9YLfVPgppc (option B).

## Global Constraints

- Branching: feature branch `quiet-refresh-on-return`, cut from `presenter-release-4.11.0`. It merges back into `presenter-release-4.11.0`, never into `main` (AGENTS.md). PR body starts with `Solves #182`.
- Commits and tests: per AGENTS.md, **don't commit, and don't add or change tests, until the user says so**. Test steps and the commit step are marked "(after go-ahead)". Existing tests must still compile unchanged, so the new constructor parameters have defaults (`logId: Long = 0L`).
- The CHANGELOG and version are edited only by the release branch's own commits, not here. Suggested bullet for later: `Quietly refresh logs when returning to the app`.
- Changes stay inside `present-app`. `LogTable.COLUMN_ID` from `devin-write` is only read, not changed.
- Gradle needs JDK 17 (not 21 or 24). The cloud container has only JDK 21: install one (`apt-get install -y openjdk-17-jdk-headless`, then `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`), or on the user's Mac use the AGENTS.md path.
- Not in scope: refreshing filter chips quietly, and the #155 recreation gap (only the first page comes back after a recreation).

## Review Focus

1. **Scroll position.** With new rows above, the first visible row (including a pinned sticky session header) stays put. At the top, the list jumps to the new rows. Check for a one-frame flash at the top, since `scrollToItem(0)` runs after the layout (Task 4 Step 3, Task 5 by hand).
2. **Paging after a prepend.** "Load more" drops rows already loaded, and the end of the list still comes from the raw page size (`checkPaginationFinish` runs before the drop; Task 2 Step 4).
3. **Wrong-filter results.** A quiet result that arrives after a filter, search text or refresh change is dropped. Any other load cancels the quiet job (Task 4 Step 2).
4. **Duplicate `LazyColumn` keys** crash Compose. Date headers can repeat (`lastDayHeaderDate` isn't reset on filter switch), so `logRowKeys` must make every key unique (Task 1 Step 3).
5. **Hint lifecycle.** The pill clears at the top. The divider moves only when the pill was gone, and both clear when `uiState` replaces the list (filter, search, refresh, clear logs). Repeated-log groups don't merge across the divider (Task 3).

---

### Task 1: Log ids on rows and stable list keys

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/log/TextLogItemData.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/log/ImageLogItemData.kt`
- Create: `present-app/src/main/java/com/khosravi/devin/present/log/LogItemDataIds.kt`
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logic/LogRowKeys.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/ReaderViewModel.kt` (`logItemDataFactory`)
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogScreen.kt` (`LazyColumn` keys)
- Test: `present-app/src/test/java/com/khosravi/devin/present/present/logic/LogRowKeysTest.kt`

**Interfaces:**
- Produces: `TextLogItemData(…, meta, val logId: Long = 0L)` and `ImageLogItemData(…, timePresent, val logId: Long = 0L)` (new last parameter).
- Produces: `fun LogItemData.logIdOrNull(): Long?`. Text → `logId`, Http → `logId.rawId`, Image → `logId`, SessionStart → `detail.logId`, Replicated → `list.first().logId`, RepeatedChild → `log.logId`, Date → `null`; the Task 3 divider → `null`.
- Produces: `fun logRowKeys(rows: List<LogItemData>): List<String>`, same size and order as `rows`. Keys: `t-<id>`, `h-<id>`, `i-<id>`, `s-<id>`, `g-<first id>`, `c-<id>`, `d-<presentDate.dumbed.hashCode()>`, `new-divider`. A key already used gets `#2`, `#3`… added.

- [ ] **Step 1: Ids.** Add the parameters. In `logItemDataFactory` pass `it.id` to every `TextLogItemData` (session-start `detail` too) and `ImageLogItemData`. Check `ContentProviderLogsDao.mapToHttpModel` builds `LogId(it.id)`; if it doesn't, say so in the commit message.
- [ ] **Step 2: `logIdOrNull`** in `LogItemDataIds.kt`, as above (an exhaustive `when` over the sealed interface).
- [ ] **Step 3: `logRowKeys`** in `LogRowKeys.kt`, as above.
- [ ] **Step 4: Use the keys.** In `LogScreen`, compute `val keys = remember(rowItems) { logRowKeys(rowItems.map { it.data }) }` and use `keys[index]` for both `stickyHeader(key = …)` and `item(key = …)`. Delete the "synthetic position+type marker" comment.
- [ ] **Step 5: Tests** (after go-ahead), `LogRowKeysTest`:
  - `keys come from log ids, not positions`: keys of `[text(5), text(4)]` are `["t-5","t-4"]`; keys of `[text(6), text(5), text(4)]` end with `["t-5","t-4"]`.
  - `duplicate keys are made unique`: two date headers for the same day give `["d-X", "d-X#2"]`, all distinct.
  - `session start and group keys use the first log id`: `s-<detail id>`, `g-<first copy id>`.
- [ ] **Step 6: Build.** `./gradlew :present-app:assembleDebug :present-app:testDebugUnitTest` → BUILD SUCCESSFUL.

### Task 2: Fetch newer logs, merge them, drop paging duplicates

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/data/model/GetLogsQueryModel.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/data/ContentProviderLogsDao.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/ReaderViewModel.kt`
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logic/NewLogsMerge.kt`
- Test: `present-app/src/test/java/com/khosravi/devin/present/present/logic/NewLogsMergeTest.kt`

**Interfaces:**
- Produces: `GetLogsQueryModel(…, val idGreaterThan: Long? = null)`. The DAO adds ` AND ${LogTable.COLUMN_ID} > ?` when it is set, threaded through `collectLogs` → `queryLogList` → `queryLogListAsCursor` like `timeLessThan`.
- Produces: `ReaderViewModel.fetchNewLogs(filter: FilterItem, searchText: String?, newerThanId: Long): Flow<List<LogItemData>>`. It uses `buildQueryGet(filter, pageInfo, searchText).copy(page = null, idGreaterThan = newerThanId)`. Date headers use a **local** day tracker, so `lastDayHeaderDate` (paging state) is untouched. It doesn't touch `pageInfo` or `uiState`.
- Produces (`NewLogsMerge.kt`, pure):
  - `fun newestLogId(rows: List<LogItemData>): Long?`: the max `logIdOrNull()`, or null if no row has one.
  - `fun prependNewLogs(old: List<LogItemData>, new: List<LogItemData>): List<LogItemData>`: drops `new` rows whose id is already in `old`. If `old` starts with a date header for a day that also has a header in `new`, it drops `old`'s first header.
  - `fun appendDistinct(old: List<LogItemData>, page: List<LogItemData>): List<LogItemData>`: drops `page` rows whose id is already in `old` (headers stay).
  - `fun countLogRows(rows: List<LogItemData>): Int`: rows with a log id.

- [ ] **Step 1: Query.** Add `idGreaterThan` and the `WHERE` part. Check `DevinContentProvider.buildSelectLogQuery` adds no `LIMIT` when `pageIndex`/`itemCount` are null. If it does add one, stop and ask (a fix there belongs to the `write` component).
- [ ] **Step 2: Headers.** Give `addDateHeadersByDay` a `pagingDay: Boolean = true` parameter. With `false` it uses a local `var lastDay: Int? = null` instead of the `lastDayHeaderDate` field.
- [ ] **Step 3: `fetchNewLogs`** as above, `flowOn(Dispatchers.Default)` like `getLogListOfFilter`.
- [ ] **Step 4: Merge functions** in `NewLogsMerge.kt`. `nextPage` stays as it is: `checkPaginationFinish` already runs on the raw page, before the activity drops duplicates.
- [ ] **Step 5: Tests** (after go-ahead), `NewLogsMergeTest`:
  - `newest id is the max id`: `[date, text(7), http(9), text(3)]` → `9`; `[date]` → `null`.
  - `new rows go in front`: `prependNewLogs([text(5)], [text(7), text(6)])` → ids `[7, 6, 5]`.
  - `same day header is not repeated`: old `[dateD1, text(5)]`, new `[dateD1, text(6)]` → `[dateD1(new), text(6), text(5)]`, one header.
  - `different day keeps both headers`: old `[dateD1, text(5)]`, new `[dateD2, text(6)]` → 4 items.
  - `rows already loaded are dropped`: `prependNewLogs([text(5)], [text(5)])` → `[text(5)]`. `appendDistinct([text(9), text(8)], [text(8), text(7)])` → ids `[9, 8, 7]`.
  - `count skips headers`: `countLogRows([date, text(1), http(2)])` → `2`.
- [ ] **Step 6: Build.** `./gradlew :present-app:assembleDebug :present-app:testDebugUnitTest` → BUILD SUCCESSFUL.

### Task 3: Divider row and pill UI

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/log/NewLogsDividerItemData.kt`
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logic/NewLogsDivider.kt`
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logitem/NewLogsDividerRow.kt`
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logitem/NewLogsPill.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogScreen.kt`
- Modify: `present-app/src/main/res/values/strings.xml`
- Test: `present-app/src/test/java/com/khosravi/devin/present/present/logic/NewLogsDividerTest.kt`

**Interfaces:**
- Produces: `class NewLogsDividerItemData(val count: Int) : LogItemData` (key `new-divider`, `logIdOrNull()` = null).
- Produces: `fun List<LogItemData>.withNewLogsDivider(boundaryId: Long?): List<LogItemData>`. It inserts the divider before the first row that has an id `<= boundaryId` and isn't a session start, but only if some row before it has an id `> boundaryId`. `count` = rows with an id `> boundaryId`, session starts excluded. With a null boundary, or no newer row, the list is returned unchanged.
- Produces: `@Composable NewLogsDividerRow(count: Int)` (accent line with centered text) and `@Composable NewLogsPill(count: Int, onClick: () -> Unit)` (accent pill, "↑ N new logs").
- Produces: plurals `new_logs_pill` ("↑ %d new log" / "↑ %d new logs") and `new_logs_divider` ("%d new log above" / "%d new logs above").
- Produces: `LogScreen(…, newLogsCount: Int, newLogsBoundaryId: Long?, scrollToTopRequest: Int, onAtTopChange: (Boolean) -> Unit, …)`, new parameters right after `isRefreshing`.

- [ ] **Step 1: Divider logic** in `NewLogsDivider.kt`, as above.
- [ ] **Step 2: List order.** In `LogScreen`'s `rowItems`, after `sessionStartsFirst()`, map to data, apply `withNewLogsDivider(newLogsBoundaryId)`, **then** group (when `groupRepeatedLogs` is on). Check that `CountingReplicatedTextLogItemDataOperation` only folds back-to-back `TextLogItemData` and passes other items through, so the divider splits groups. Add `newLogsBoundaryId` to the `remember` keys.
- [ ] **Step 3: Rows.** Add a `NewLogsDividerItemData -> NewLogsDividerRow(data.count)` branch to `LogItemDataRow` (`ImportLogScreen` never gets one, but the `when` is exhaustive).
- [ ] **Step 4: Pill.** Wrap the `LazyColumn` in `Box(Modifier.fillMaxWidth().weight(1f))` (the list fills it) and place `if (newLogsCount > 0) NewLogsPill(newLogsCount, onClick = { scope.launch { listState.animateScrollToItem(0) } })` aligned `TopCenter` with a small top padding. `scope` = `rememberCoroutineScope()`.
- [ ] **Step 5: Top state.** Report `listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0` through `snapshotFlow { … }.distinctUntilChanged().collect(onAtTopChange)` in a `LaunchedEffect(listState)`. Add `LaunchedEffect(scrollToTopRequest) { if (scrollToTopRequest > 0) listState.scrollToItem(0) }`.
- [ ] **Step 6: Tests** (after go-ahead), `NewLogsDividerTest`:
  - `divider goes before the first old row`: `[text(7), text(6), text(5)].withNewLogsDivider(5)` → `[7, 6, divider(count=2), 5]`.
  - `session start above new rows stays above the divider`: `[session(3), text(7), text(5)]` with boundary 5 → `[session(3), text(7), divider(1), text(5)]`.
  - `no divider when nothing is newer`: boundary 9 on `[text(7)]` → unchanged.
  - `null boundary adds nothing`: unchanged.
  - `groups don't merge across the divider`: `[textA(7), textA(6)]` with boundary 6, then `groupRepeatedAndExpand(emptySet())` → `[textA(7), divider, textA(6)]`, no `ReplicatedTextLogItemData`.
- [ ] **Step 7: Build.** `./gradlew :present-app:assembleDebug :present-app:testDebugUnitTest` → BUILD SUCCESSFUL.

### Task 4: Trigger and state in `LogActivity`

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt`

**Interfaces:**
- Consumes: Task 2's `fetchNewLogs`, `newestLogId`, `prependNewLogs`, `appendDistinct`, `countLogRows`; Task 3's `LogScreen` parameters.
- Produces (private state): `newLogsCount by mutableStateOf(0)`, `newLogsBoundaryId by mutableStateOf<Long?>(null)`, `scrollToTopRequest by mutableStateOf(0)`, `isListAtTop = true`, `quietJob: Job?`, `skipNextQuietRefresh: Boolean`.

- [ ] **Step 1: Trigger.** In `onCreate`, set `skipNextQuietRefresh = true` (this covers both the first start and a recreation, since a recreation runs `onCreate` again). Override `onStart`: `if (skipNextQuietRefresh) skipNextQuietRefresh = false else quietRefresh()`.
- [ ] **Step 2: `quietRefresh()`.**
  - Return when `optCurrentFilterItem()` is null, `isRefreshing`, `!isFilterRowEnabled` (a filter switch is loading), or a page load is running (expose `LogPaginationState.isLoading`).
  - With no `newestLogId(logRows)`, call `viewModel.search(filter, searchRow?.text)` (quiet: no callback id, so no toast) and return.
  - Otherwise `quietJob?.cancel()` and `quietJob = lifecycleScope.launch { … }`: take `viewModel.fetchNewLogs(filter, text, topId).first()`. Drop the result if `selectedFilterId != filter.id` or `searchRow?.text != text`. Return if `countLogRows(new) == 0`.
  - Then, in this order: if `newLogsCount == 0`, set `newLogsBoundaryId = topId` (move the divider); `logRows = prependNewLogs(logRows, new)`; if `isListAtTop`, increment `scrollToTopRequest`, else add the count to `newLogsCount`.
  - Cancel `quietJob` at the start of `selectNewFilter`, `refreshLogsAndFilters`, `loadMoreItems`, the search collector, and `onClearLogs`.
- [ ] **Step 3: Wire the screen.** Pass the four new `LogScreen` arguments. `onAtTopChange = { isListAtTop = it; if (it) newLogsCount = 0 }`.
- [ ] **Step 4: Clear on replace.** In `onUiStateFlowResult`, when `result.logList != null`, set `newLogsCount = 0` and `newLogsBoundaryId = null`. Every filter, search, refresh and clear-logs result goes through here.
- [ ] **Step 5: Paging.** In `setupNextPageFlow`, use `logRows = appendDistinct(logRows, it.logs)`.
- [ ] **Step 6: Build.** `./gradlew :present-app:assembleDebug :present-app:testDebugUnitTest` → BUILD SUCCESSFUL.

### Task 5: Check by hand and commit

- [ ] **Step 1: Check by hand** (connected device, `sample-app` writing logs):
  - Scroll down to the middle, switch to `sample-app`, write logs, come back. Nothing moves, there is no spinner or toast, and the pill shows the right count. Scroll up: the divider sits between the new and old rows, and the pill goes away at the top.
  - Come back while at the top: the new logs show, with the divider and no pill.
  - Write more than 40 logs while away, mid-list: the row being read is still there. Then scroll to the end: no duplicate rows, and "load more" stops at the real end.
  - With a tag chip selected (and with tag search text), only matching logs are added and counted. The chips don't change.
  - Return twice without reaching the top: the pill count adds up and the divider stays put.
  - A filter change, manual or pull refresh, or clear logs removes the pill and the divider. Rotating doesn't trigger a quiet refresh. Coming back from Settings does.
- [ ] **Step 2: Commit** (after go-ahead), all tasks together: `Quietly refresh logs when returning to the app`.
