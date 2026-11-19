# Edit Custom Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A custom filter chip's long-press menu gets **Edit**, which opens the create-filter dialog filled with the filter's title, tags and search text; confirming saves over it, moving the pin if the title changed, and a title that another filter already uses is rejected.

**Architecture:** A filter's id is its title and the SharedPreferences key (`filter_v2`, shared by all clients). So an edit is "write the new entry, remove the old key if the title changed, move the pin, keep the original save timestamp so the chip keeps its position". The data rules live in `FilterRepository` (unit tested with Robolectric). The dialog is reused through the existing `createFilter` NavHost destination, which gains optional "editing" arguments.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation Compose, SharedPreferences, JUnit 4 + Robolectric.

**Spec:** GitHub issue nasserkhosravi/devin-proj#138 (milestone "presnter 4.11.0").

## Global Constraints

- Branching: feature branch `edit-custom-filter` was cut from `pull-to-refresh` (stacked on PRs #184, #185 and the #175 branch; this plan touches `LogActivity.kt`, `ReaderViewModel.kt` and `FilterChipsRow.kt` too, so expect trivial merge work). It merges into `presenter-release-4.11.0`, never `main` (AGENTS.md).
- Commits and tests: per AGENTS.md, **don't commit, and don't add or change tests, until the user says so**. Test and commit steps run only after that go-ahead.
- Gradle needs JDK 17: prefix with `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`.
- A filter's id is its title; the pref key is the id for **every client** (not per client), so "title taken" means the key exists, whoever owns it.
- Duplicate titles (spec): saving without changing the title is always allowed; a different filter's title blocks the save with the field error "A filter with this name already exists". The same check applies when creating. `FilterRepository.saveFilter` keeps replacing by id (an existing test pins that); the block is in the dialog.
- Changes stay inside `present-app`.

## Calls made in this plan (the spec leaves them open)

- The edited chip keeps its position (the original `_timestamp` is kept, even on rename).
- After a confirmed edit the edited filter becomes the selected chip and its logs reload, as after creating one.
- The old filter's usage record (`FilterUsageRepository`) is dropped on rename; it isn't migrated.
- No new analytics event.

## Review Focus

1. **Rename onto another filter** must not overwrite it, even if the dialog check were bypassed: the repository refuses (Task 1 test).
2. **Renaming moves the pin** and leaves other pins alone; an unpinned filter stays unpinned (Task 1 tests).
3. **A saved tag the client no longer logs** stays checked in the edit dialog (existing `preselectedTags` behavior; Task 2, by hand).
4. **Blank title while editing** falls back to the tags joined with " + ", and that title goes through the duplicate check (Task 2).
5. **Rotation with the edit dialog open** keeps the edited values (they are `rememberSaveable`, seeded from the destination arguments; Task 2, by hand).

---

### Task 1: Repository rules

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/data/FilterRepository.kt`
- Test: `present-app/src/test/java/com/khosravi/devin/present/data/FilterRepositoryTest.kt` (extend; reuse its `filter(...)`, `saveInOrder(...)` helpers)

**Interfaces:**
- Produces: `fun isTitleTaken(title: String): Boolean` — true when a filter with that id exists (any client).
- Produces: `fun replaceFilter(originalId: String, data: CustomFilterItem, clientId: String): Boolean`:
  - Returns `false` and changes nothing when `data.id` is empty, or when `data.id != originalId` and `isTitleTaken(data.id)`.
  - Otherwise writes `data` under `data.id` with the **original entry's `_timestamp`** (now, if the original is missing), and returns the commit result.
  - If the id changed: removes the old key, and if the old id was pinned, pins the new id and unpins the old one. An unpinned original stays unpinned.

- [ ] **Step 1: Failing tests** (after go-ahead), each with a fresh `repository`:
  - `replaceFilter keeps position and pin when the title is unchanged`: save `a`, `b`, `c` for client `x`, pin `b` (`saveAsPinned`); `replaceFilter("b", filter("b", tags = listOf("db"), search = null), "x")` → true; the list order is still `a, b, c`, `b`'s criteria is `CustomFilterCriteria(listOf("db"), null)`, and `b` is pinned.
  - `renaming keeps position and moves the pin`: same setup; `replaceFilter("b", filter("b2"), "x")` → order `a, b2, c`, `b` is gone, `b2` is pinned, and `getPinnedListIds()` doesn't contain `b`.
  - `renaming an unpinned filter does not pin the new title`: `a` unpinned renamed to `a2` → `a2.ui.isPinned == false`.
  - `renaming onto another filter is refused`: save `a`, `b`; `replaceFilter("a", filter("b", tags = listOf("zzz")), "x")` → false; `a` and `b` unchanged (`b` still has tag `net`).
  - `isTitleTaken is true for any client's saved title`: saved under client `y`, `isTitleTaken("t")` → true; `isTitleTaken("nope")` → false.
- [ ] **Step 2: Run, expect FAIL** (unresolved reference): `./gradlew :present-app:testDebugUnitTest --tests "*FilterRepositoryTest"`
- [ ] **Step 3: Implement both.** Add a `timestamp: Long = Date().time` parameter to the private `toJson` (default keeps `saveFilter` unchanged) and read the original's timestamp from its stored JSON.
- [ ] **Step 4: Run, expect PASS** (the existing tests in the class too).
- [ ] **Step 5: Commit** (after go-ahead): `Add repository rules for editing a custom filter`

### Task 2: Edit action, dialog and wiring

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/FilterChipsRow.kt` (`FilterChipAction.Edit`, menu item)
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/FilterDialog.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/CreateFilterDestination.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/ReaderViewModel.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt`
- Modify: `present-app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Task 1's `isTitleTaken` and `replaceFilter`.
- Produces: `FilterChipAction.Edit(val item: CustomFilterItem)`; strings `menu_action_edit` ("Edit"), `filter_error_title_taken` ("A filter with this name already exists").
- Produces: `FilterDialog(availableTags, preselectedTags, onConfirm, initialTitle: String = "", initialSearchText: String = "", originalTitle: String? = null, isTitleTaken: (String) -> Boolean = { false })`.
- Produces: `createFilterDestination(loadAvailableTags, isTitleTaken: (String) -> Boolean, onConfirm: (CustomFilterItem, originalTitle: String?) -> Unit)`, and `fun NavController.navigateToEditFilter(item: CustomFilterItem)`.
- Produces: `ReaderViewModel.isFilterTitleTaken(title: String): Boolean` and `ReaderViewModel.updateFilter(originalId: String, data: CustomFilterItem, callbackId: String? = null)`.

- [ ] **Step 1: Strings and action.** Add the two strings and `FilterChipAction.Edit`. In the chip menu's `if (filter is CustomFilterItem)` block, add `item(R.string.menu_action_edit) { onAction(FilterChipAction.Edit(filter)) }` above Remove.
- [ ] **Step 2: `FilterDialog`.** Seed `title`/`searchText` `rememberSaveable` state from `initialTitle`/`initialSearchText`. Add `var showTitleError by rememberSaveable { mutableStateOf(false) }`; the title `OutlinedTextField` gets `isError = showTitleError` and a `supportingText` with `filter_error_title_taken`, and typing in it clears the error. In Confirm, after the existing no-tag check and after computing the final title (blank → tags joined), if `fTitle != originalTitle && isTitleTaken(fTitle)` set `showTitleError = true` and return without calling `onConfirm`.
- [ ] **Step 3: Destination.** Add nullable arguments `editTitle` (String) and `editSearchText` (String) beside `preselectedTags`. When `editTitle != null` the route is editing: pass `initialTitle = editTitle`, `initialSearchText`, `originalTitle = editTitle` to `FilterDialog`, and tags from `preselectedTags`. `onConfirm` forwards `(item, editTitle)`. `navigateToEditFilter(item)` navigates by node id with a Bundle holding the tags array, `item.id` and `item.criteria.searchText`. Creating keeps working with the new arguments absent (`isTitleTaken` still applies to it).
- [ ] **Step 4: ViewModel.** `isFilterTitleTaken` delegates to `filterRepository.isTitleTaken`. `updateFilter`: on `Dispatchers.Default`, read whether `originalId` is currently pinned, call `replaceFilter(originalId, data, clientId)` (throw like `addFilter` does when it returns false), set the saved item's `ui.isPinned` from that, replace the list entry that is a `CustomFilterItem` with id `originalId` **at its index** (not append), drop `FilterUsageRepository` usage of `originalId` when the id changed, and publish a `ResultUiState` with `filterIdSelection = data.id` and the given `callbackId`.
- [ ] **Step 5: `LogActivity`.** Pass `isTitleTaken = viewModel::isFilterTitleTaken` to `createFilterDestination`; its `onConfirm` pops back to `ROUTE_LOGS`, then calls `addFilter(it)` when `originalTitle == null`, else `viewModel.updateFilter(originalTitle, it, CALLBACK_ID_EDIT_FILTER)`. `onFilterAction` handles `Edit` by `navController.navigateToEditFilter(action.item)` guarded by `isCurrentEntryResumed()` like `openCreateFilter`. In `onUiStateFlowResult`, `CALLBACK_ID_EDIT_FILTER` selects the filter whose id is `updateInfo.filterIdSelection` (`filterList.firstOrNull { it.id == … }?.let(::selectNewFilter)`). Add `private const val CALLBACK_ID_EDIT_FILTER = "edit_filter"`.
- [ ] **Step 6: Build and unit tests.** `./gradlew :present-app:assembleDebug` and `:present-app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 7: Check by hand** (device; create custom filters `one` and `two` first):
  - Long-press `one`: **Edit** is in the menu (not on tag chips). It opens the dialog with `one`'s title, tags checked and search text filled.
  - Change the search text and confirm: the chip keeps its place, becomes selected and its logs reload. Reopen Edit: the new text is there.
  - Pin `one`, edit its title to `uno`: the chip is `uno`, still pinned, same place; the old `one` is gone (also after leaving and reopening the client).
  - Edit `uno` and set the title to `two`: the title field shows the error and the dialog stays open. Create a new filter titled `two`: same error.
  - Edit and clear the title: the saved title becomes the tags joined with " + ".
  - A filter whose tag no longer has logs (use a tag from a cleared client): the tag stays checked in the dialog.
  - Rotate with the dialog open: the edited values stay.
- [ ] **Step 8: Commit** (after go-ahead): `Add Edit to custom filter chips`
