# ImportLogActivity → Jetpack Compose migration — design

Date: 2026-10-16
Branch: `compose-migration` (one commit per screen)

## Context

`present/ImportLogActivity.kt` (XML `activity_import_log.xml`: search `TextInputLayout` + `RecyclerView`,
FastAdapter) shows logs from an imported JSON file:

- Reads the Uri from `KEY_DATA` synchronously on the main thread; `null` → toast
  `error_msg_something_went_wrong`, empty → `error_msg_empty_file_text`, then `finish()`.
- `viewModel.convertImportedLogsToPresentableLogItems(content)` (ReaderViewModel) → `List<LogItemData>`
  (date headers added; replicated grouping is disabled there) → FastAdapter items via `toItemViewHolder`.
- Search field filters items by `contains(text, ignoreCase)` on text logs (`TextLogItem`,
  `ReplicatedTextLogItem`); non-text items always stay visible.
- Tap text log → `LogItemDetailActivity` (the bridge); tap HTTP log → "Coming soon" toast.
- **Not reachable from the UI today**: `LogActivity` registers `importIntentLauncher` but the menu item
  that launches it was commented out before this work. Keep `ImportLogActivity.intent()` and the
  launcher wiring as they are (don't re-enable the menu item — out of scope).

This is the last user of the FastAdapter **log item** classes and of the bridge
`LogItemDetailActivity`, so migrating it lets them be deleted.

## Decisions (autonomous, per the user's "migrate all screens" instruction)

1. **Activity with `setContent` and its own small `NavHost`**: `"imported"` list destination +
   the same `logDetail` destination as `LogActivity` (Bundle-by-id navigation via `LogDetailArgs`).
   Extract the detail destination into a shared
   `fun NavGraphBuilder.logDetailDestination(navController: NavHostController, calendar: CalendarProxy, startRoute: String)`
   (in `LogDetailArgs.kt` or a new `LogDetailDestination.kt`) and use it from both Activities.
   Back from detail = `popBackStack(startRoute, inclusive = false)` (same race fix as LogActivity).
   Text-log taps keep the RESUMED double-tap guard (move `isCurrentEntryResumed()` to a shared place).
2. **Delete the bridge `LogItemDetailActivity`** (+ manifest entry, `AppComponent.inject(LogItemDetailActivity)`).
3. **Reuse the Compose log rows** from `present/logitem/` (`DateHeaderRow`, `TextLogRow`, `HttpLogRow`,
   `ImageLogRow`, `SessionStartRow`) — same mapping as `LogScreen`'s `LogListRow` (replicated items:
   exhaustiveness guard only, as there). If `LogListRow` is private in LogScreen.kt, make it `internal`
   and reuse it rather than duplicating the `when`.
4. **Search**: `OutlinedTextField` with local state (no debounce) above the list; filtered list computed
   with `remember(items, query)`; same predicate (text logs by `contains(ignoreCase)`, others always shown).
   Search field styling like the XML (outlined, hint as label/placeholder — read the XML).
5. **Read the file off the main thread** (`Dispatchers.IO` inside a `LaunchedEffect`/coroutine), then
   convert; error/empty → same toasts + `finish()`. While loading show nothing (or a small progress).
6. Title: XML had no toolbar; keep no top bar (parity). System back finishes.
7. HTTP log tap → same "Coming soon" toast.

## Deletions (grep each before deleting — must have no remaining users)

- `res/layout/activity_import_log.xml`, `LogItemDetailActivity.kt`.
- FastAdapter log item classes in `log/`: `TextLogItem`, `HttpLogItemView`, `ImageLogItem`,
  `SessionStartLogItem`, `HeaderLogDateItem`, `ReplicatedTextLogItem`, `TextLogSubItem`, and the
  `toItemViewHolder` extension in AppExt.kt; their layouts (`item_log.xml`, `item_http.xml`,
  `item_image_log.xml`, `item_session_start.xml`, `item_header_log_date.xml`,
  `item_replicated_text_log.xml`) — **only** if nothing else (notably `present/http/` items, which are
  migrated later) references them. Keep the `*Data` classes. Keep FastAdapter dependencies (the HTTP
  detail screen still uses them; removed in the cleanup step).

## Verification

Build + unit-test compile. On device (the screen is not reachable from the UI): if possible, temporarily
launch it for testing only via a debug path the tester sets up outside the source tree — otherwise
report it as build-verified, plus verify `LogActivity`'s log detail still works after the destination
extraction (open/back/double-back/copy).
