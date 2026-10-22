# LogActivity → Jetpack Compose migration — design

Date: 2026-08-30
Branch: `compose-log-activity` (based on `compose-client-login`)

## Context

`LogActivity` is the main log viewer screen and the last/biggest remaining screen in the
present-app gradual Compose migration (see `docs/superpowers/plans/` history and prior branches:
`compose-log-detail-dialog`, `compose-filter-dialog`, `compose-starter-activity`,
`compose-client-login`). It's FastAdapter-heavy: a horizontal filter-chip row, a vertical
paginated log list with 5 item types, search, context menus, and an export entry point.

`ReaderViewModel` already emits plain domain types (`List<LogItemData>`, `List<FilterItem>`) —
the `List<LogItemData>.toItemViewHolder()` mapping to FastAdapter `GenericItem`s happens only in
`LogActivity` itself. This means the ViewModel needs **no changes**; only the UI layer is
migrated.

## Decisions

1. **FastAdapter removal for this screen's UI code, constrained by `ImportLogActivity`.**
   `ImportLogActivity` (still XML/FastAdapter, not yet migrated) transitively depends on
   `TextLogItem`, `HttpLogItemView`, `ImageLogItem`, `SessionStartLogItem`, `HeaderLogDateItem`,
   and `ReplicatedTextLogItem`/`TextLogSubItem` via `AppExt.toItemViewHolder()` (which it calls
   directly) and its own direct `TextLogItem`/`HttpLogItemView`/`ReplicatedTextLogItem`
   references — so **none of those classes, their layouts, or `toItemViewHolder()` can be
   deleted**. `LogActivity`'s new Compose code does its own `LogItemData` → composable mapping
   instead of calling `toItemViewHolder()`.
   Only classes exclusively used by `LogActivity` are deleted: `FilterItemViewHolder`,
   `SearchItemView`, `EndlessScrollListener`, and the `SingleSelectionItemAdapter` usage in
   `LogActivity` (verified via full-codebase grep — no other call sites).
   `ReplicatedTextLogItemData` itself (the data class, distinct from the `ReplicatedTextLogItem`
   view) is still unproduced dead data (`CountingReplicatedTextLogItemDataOperation` stays
   commented out in `ReaderViewModel`) but its view class stays since `ImportLogActivity`
   references it by type.
2. **Filter chip row simplified to a single scrollable row.** Current XML switches
   `LinearLayoutManager` → `StaggeredGridLayoutManager` (2 rows, wrapped) when there are more
   than 4 filters. Compose version always uses a horizontally-scrollable `LazyRow` — drops the
   2-row wrap special case, avoids the experimental `FlowRow` API.
3. **Image loading stays on existing Glide API, no new dependency.** `ImageLogItem`'s
   `Glide...CustomTarget<File>` bitmap loading is ported into a `LaunchedEffect(url)` that writes
   into a `mutableState<Bitmap?>`, rendered via `Image(bitmap = ...)`. No
   `com.github.bumptech.glide:compose` dependency added (avoids repeating past BOM/version-pin
   pain from this migration).
4. **Search bar keeps its current architecture**: still injected as index-0 of the rendered log
   list when `viewModel.getSearchItemHint(filter) != null`, not split into a separate pinned
   Composable slot. Same debounce logic (`searchInput` `MutableSharedFlow` + 700ms debounce) is
   reused, just fed by a Compose `TextField`'s `onValueChange` instead of `SearchView`'s query
   listener.

## Architecture

`LogActivity` becomes a thin state holder, same shape as the already-migrated `StarterActivity`:

```kotlin
class LogActivity : BaseActivity() {
    private val viewModel by lazy { ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java] }
    private val logDetailDialogHost by lazy { LogDetailDialogHost(this, ...) }   // already Compose
    private val filterDialogHost by lazy { FilterDialogHost(this, ...) }          // already Compose

    private var filterList by mutableStateOf<List<FilterItem>>(emptyList())
    private var logItems by mutableStateOf<List<LogListRowItem>>(emptyList())     // see model below
    private var isFilterRowEnabled by mutableStateOf(true)
    private var isLoading by mutableStateOf(false)

    override fun onCreate(...) {
        setContent { DevinTheme { LogScreen(filterList, logItems, isFilterRowEnabled, isLoading, ...callbacks) } }
        viewModel.doFirstFetch()
        // same uiState/nextPageFlow/searchInput collection as today, updating the state vars above
    }
}
```

`LogExportDialog` (still XML `BaseDialog`, not yet migrated — out of scope) is shown the same
way: `LogExportDialog.newInstance().show(supportFragmentManager, TAG)`.

## List item model

A small sealed wrapper distinguishes the search placeholder from real log rows, since
`LogItemData` itself has no "search bar" variant:

```kotlin
sealed interface LogListRowItem {
    data class Search(val hint: String, val text: String?) : LogListRowItem
    data class Log(val data: LogItemData) : LogListRowItem
}
```

`LogScreen`'s `LazyColumn` does `itemsIndexed(logItems)` and a `when` picks the composable:

| `LogItemData` subtype       | Composable        | Ported from            |
|------------------------------|--------------------|-------------------------|
| n/a (`LogListRowItem.Search`)| `SearchBarItem`    | `SearchItemView`        |
| `DateLogItemData`             | `DateHeaderItem`   | `HeaderLogDateItem`     |
| `TextLogItemData`             | `TextLogRow`       | `TextLogItem`           |
| `HttpLogItemData`             | `HttpLogRow`       | `HttpLogItemView`       |
| `ImageLogItemData`            | `ImageLogRow`      | `ImageLogItem`          |
| `SessionStartLogItemData`     | `SessionStartRow`  | `SessionStartLogItem`   |

`TextLogRow` ports the log-level → (text, icon, colors) `styleIt` logic from `TextLogItem`
verbatim (same `R.color.log_*`/`R.drawable.ic_*` resources — no new tokens needed).

## Composables

New file(s) under `present-app/src/main/java/com/khosravi/devin/present/present/` (e.g.
`LogScreen.kt`, split further only if a single file gets unwieldy):

- `LogScreen(filterList, logItems, isFilterRowEnabled, isLoading, callbacks...)` — `Scaffold` +
  `TopAppBar` + `FilterChipsRow` + `LazyColumn` + loading indicator, mirrors `StarterScreen`'s
  shape.
- `FilterChipsRow` — `LazyRow`, one `FilterChip`-like composable per `FilterItem`, selected state
  styling ported from `FilterItemViewHolder` (including the special-cased okhttp chip
  colors), `combinedClickable` for click (select) + long-click (open context `DropdownMenu`:
  pin/unpin, share-as-json for `TagFilterItem`, remove for `CustomFilterItem` — same
  `normalizeMenuToItsAvailableActions` visibility rules ported as a plain `when`).
- `SearchBarItem` — `TextField`/`OutlinedTextField` replacing `SearchView`, calls
  `onSearchTextChange` on every keystroke (existing 700ms debounce in the Activity absorbs it).
- `DateHeaderItem`, `TextLogRow`, `HttpLogRow`, `SessionStartRow` — straightforward ports of
  their FastAdapter `bindView` logic into `@Composable` functions taking the `*LogItemData` +
  `CalendarProxy`.
- `ImageLogRow` — `LaunchedEffect(data.data.url)` running the existing
  `Glide.with(context).asFile().load(url).into(CustomTarget<File>...)` call, writing
  `Bitmap?`/loading-state into local `remember { mutableStateOf(...) }`; click-to-copy URL
  (`setClipboard` + Toast) ported as `Modifier.clickable`.

## Interactions

- Text/Http log row click: same dispatch as today —
  `TextLogItemData` → `logDetailDialogHost.show(data)`, `HttpLogItemData` →
  `HttpLogDetailActivity.startActivity(this, data.logId)`.
- Toolbar: icon actions (clear logs, refresh) as `IconButton` + `painterResource` on the existing
  drawables (`baseline_clear_24`, `baseline_refresh_24`) — no `material-icons-extended` dependency
  (established gotcha from earlier migrations: core icon set is too small). Remaining text-only
  actions (export, create filter, clear filters, toggle theme) live in an overflow `DropdownMenu`
  behind a "more" `IconButton`, replacing `onCreateOptionsMenu`/`onOptionsItemSelected` and
  `menu/main_menu.xml`.
- Pagination: `EndlessScrollListener` replaced by a `derivedStateOf` on the `LazyListState`
  checking when the last visible item index is within N of `logItems.lastIndex`, calling
  `viewModel.nextPage(...)` — same page-tracking (`PageInfo`, `pageInfo.isFinished`) in
  `ReaderViewModel`, untouched.
- Notification tap-through (`targetTag` / `EXTRA_TARGET_TAG` / `withNotificationTarget()` /
  `selectNotificationTargetIfNeeded()`) logic is UI-agnostic (operates on `List<FilterItem>`) —
  ported unchanged into the Activity.

## Deleted files

- Kotlin: `FilterItemViewHolder.kt`, `SearchItemView.kt`, `EndlessScrollListener.kt`
  (all confirmed `LogActivity`-exclusive via full-codebase grep).
- Layouts: `activity_log.xml` (LogActivity's own), `item_search.xml`, `item_filter.xml`
  (both exclusive to the deleted view classes above).
- Menus: `menu/main_menu.xml`, `menu/menu_filter_item_quick_action.xml` (LogActivity-exclusive).

**Explicitly NOT deleted** (still required by `ImportLogActivity`, unmigrated):
`TextLogItem.kt`, `HttpLogItemView.kt`, `ImageLogItem.kt`, `SessionStartLogItem.kt`,
`HeaderLogDateItem.kt`, `ReplicatedTextLogItem.kt`, `TextLogSubItem.kt`,
`ReplicatedTextLogItemData.kt`, their layouts (`item_log.xml`, `item_http.xml`,
`item_image_log.xml`, `item_header_log_date.xml`, `item_replicated_text_log.xml`), and
`AppExt.toItemViewHolder()`. `LogActivity`'s Compose code has its own separate
`LogItemData` → composable `when` instead of reusing `toItemViewHolder()`.

## Out of scope

- `ReaderViewModel` — untouched.
- `LogExportDialog` — still XML/`BaseDialog`, triggered the same way.
- `HttpLogDetailActivity`, `ImportLogActivity` — still XML, started via `Intent` unchanged.
- `LogDetailDialogHost`, `FilterDialogHost` — already Compose from prior branches, reused as-is.

## Testing / verification

No instrumented UI tests (per established pattern). Manual verification on emulator via
`adb`/screenshots once implemented: filter chip select/pin/unpin/remove/share, scroll pagination,
search debounce, each log item type renders (text/http/image/session-start/date-header), toolbar
actions (refresh, clear logs, clear filters, create filter, export, toggle theme), notification
tap-through target selection.
