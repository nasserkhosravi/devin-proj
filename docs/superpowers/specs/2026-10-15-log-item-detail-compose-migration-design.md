# LogItemDetailActivity → Jetpack Compose migration — design

Date: 2026-10-15
Branch: `compose-log-item-detail` (based on `compose-log-activity`)

## Context

`presenter-release-4.7.0` replaced the old `LogDetailDialog` with a full-page XML screen,
`LogItemDetailActivity` (toolbar with a copy action; Tag/Time rows; selectable Message; optional
Meta section). That merge (commit `5b40723` on `compose-log-activity`) dropped our earlier
Compose `LogDetailDialog` in favour of the new page, so the page is now XML again.

It is opened from two places:

- `LogActivity` (already Compose) — `onTextLogClick` → `LogItemDetailActivity.startActivity(this, it)`.
- `ImportLogActivity` (still XML/FastAdapter, not yet migrated) — `onTextLogItemClick`.

This is the first step of the remaining present-app migration roadmap:
LogItemDetailActivity → LogExportDialog → SettingsActivity → ImportLogActivity →
HttpLogDetailActivity → cleanup. Which presenter release the compose chain targets is
deliberately deferred.

## Decisions

1. **Navigation-compose destination inside `LogActivity`.** This is the first in-Activity
   navigation in the app. `LogActivity`'s `setContent` hosts a `NavHost` with two destinations:
   `"logs"` (existing `LogScreen`) and `"logDetail"` (new `LogItemDetailScreen`).
2. **Shared stateless screen + thin bridge Activity.** `ImportLogActivity` cannot navigate into
   `LogActivity`'s graph, so `LogItemDetailActivity` is kept as a thin `setContent` host of the
   same `LogItemDetailScreen`, used only by `ImportLogActivity`. Its public
   `startActivity(context, data)` API and Intent extras are unchanged. It is deleted when
   `ImportLogActivity` is migrated.
3. **Data passed as URL-encoded route arguments**, mirroring today's Intent extras (tag, message,
   timestamp, meta-as-JSON-string). The back stack saves them like extras, so the page survives
   process death and configuration changes. Not chosen: in-memory ViewModel state (lost on
   process death) and load-by-id (imported logs aren't in the DB).
4. **Back arrow added** to the top bar in both hosts (the XML toolbar had none). In `LogActivity`
   it pops the back stack; in the bridge Activity it calls `finish()`.

## Dependency

- Add `navigation-compose` **2.7.7** to `gradle/libs.versions.toml` (`navigationCompose` version,
  `navigation-compose` library) and to `present-app/build.gradle.kts`.
- Its POM requires Compose 1.5.1, activity-compose 1.7.0, lifecycle-viewmodel-compose 2.6.2 —
  all at or below what the pinned BOM `2023.09.02` / `activityCompose 1.7.2` provide. 2.8.x is
  **not** usable (needs Compose 1.7 → newer compiler/Kotlin).
- Verify with `./gradlew :present-app:dependencies --configuration debugRuntimeClasspath` that
  `androidx.compose.ui:ui` still resolves to exactly **1.5.2** (matches compiler 1.5.2) and
  navigation-compose to 2.7.7.

## Components

### `present/LogItemDetailScreen.kt` (new)

```kotlin
@Composable
fun LogItemDetailScreen(
    tag: String,
    message: String,
    timeText: String,     // already formatted with millis by the host
    meta: String?,
    onCopy: () -> Unit,
    onBack: () -> Unit,
)
```

- `Scaffold` + `TopAppBar` (`@OptIn(ExperimentalMaterial3Api::class)`, Material3 1.1.2):
  title `R.string.log_detail_title`; navigation icon `Icons.Default.ArrowBack` (in
  `material-icons-core`; `Icons.AutoMirrored` does not exist in this Compose version);
  action icon `R.drawable.baseline_content_copy_24`, content description
  `R.string.menu_log_detail_copy`.
- Body: `Column` with `verticalScroll(rememberScrollState())`, padding `spacing.medium` (16dp,
  = XML `spaceLarge`).
  - Tag and Time: `Row`s; label `Text` with `Modifier.fillMaxWidth(0.28f)` (XML guideline 28%),
    bold; value `Text` filling the rest. Time row `spacing.small` top margin.
  - Message: label (bold) with `spacing.medium` top margin, then value with `spacing.small` top
    margin.
  - Meta: same as Message, only when `meta != null`.
  - Text style: 14sp (`android:TextAppearance.Small`) → `MaterialTheme.typography.bodyMedium`;
    labels bold. Values keep the XML style's 16dp start padding.
  - Whole body wrapped in `SelectionContainer` (XML values were `textIsSelectable`).
- Stateless; a `@Preview` with and without meta.

### `present/LogDetailArgs.kt` (new)

Single owner of the route format.

```kotlin
object LogDetailArgs {
    const val ROUTE_PATTERN = "logDetail?tag={tag}&message={message}&timestamp={timestamp}&meta={meta}"
    val arguments: List<NamedNavArgument>   // tag/message: StringType default "";
                                            // timestamp: LongType (required);
                                            // meta: StringType nullable, default null
    fun route(data: TextLogItemData): String // Uri.encode() every string value; omit meta when null
    fun read(entry: NavBackStackEntry): Values // Values(tag, message, timestamp, meta)
}
```

### `present/LogItemDetailCopy.kt` (new)

`fun Context.copyLogDetail(tag, timeText, message, meta)` — the existing
"Tag:/Time:/Message:/Meta:" `buildString`, `setClipboardSafe`, and `R.string.copied` toast on
success. Shared by both hosts.

### `LogActivity` (changed)

- `setContent { DevinTheme { val nav = rememberNavController(); NavHost(nav, "logs") { … } } }`.
- `"logs"`: the existing `LogScreen(...)` call, unchanged except
  `onTextLogClick = { nav.navigate(LogDetailArgs.route(it)) }`.
- `"logDetail"` (`LogDetailArgs.ROUTE_PATTERN`, `LogDetailArgs.arguments`): read args, format time
  with the injected `calendar.getFormattedWithMillis(TimePresent(timestamp))` inside
  `remember(timestamp)`, render `LogItemDetailScreen(onBack = { nav.popBackStack() },
  onCopy = { copyLogDetail(...) })`.
- All Activity-level behaviour (force-update gate in `onResume`, ViewModel collection, export,
  filters) is untouched.

### `LogItemDetailActivity` (rewritten as bridge)

- Keeps: `@Inject calendar`, `getAppComponent().inject(this)`, companion `startActivity` and
  extras keys.
- `setContent { DevinTheme { LogItemDetailScreen(..., onBack = ::finish, onCopy = { copyLogDetail(...) }) } }`.
- Removes: view binding, toolbar menu handling, `onDestroy` binding cleanup.
- KDoc note: "Bridge for ImportLogActivity only; delete when ImportLogActivity is migrated."

## Behaviour notes

- Navigating to `"logDetail"` disposes the `"logs"` composition; on return:
  - Logs, filters and selected filter live in `LogActivity` fields → unaffected.
  - `LogScreen`'s `rememberLazyListState()` is saveable → scroll position is restored via the
    back-stack entry's saved state.
  - `SearchBarRow` re-inits its local text from the parent's current search value
    (`remember(sessionId) { mutableStateOf(text ?: "") }`). Edge: characters typed within the
    debounce window right before tapping a log may be lost. Accepted, but verify on device.
  - Pagination `snapshotFlow` re-launches; `LogPaginationState` already de-dupes.
- Default navigation 2.7 crossfade; no custom transitions.
- System back on `"logDetail"` pops to `"logs"` (NavHost handles it); on `"logs"` it finishes
  the Activity as before.

## Error handling

- `timestamp` is required; a missing value is a programming error (same as today's
  `getLongExtraOrFail`).
- `meta` nullable → section hidden when null.
- Every string arg is `Uri.encode`d, so `& ? / % #`, newlines and non-ASCII survive the route.
- Copy toast only when `setClipboardSafe` succeeds (unchanged).

## Deleted files

- `res/layout/activity_log_item_detail.xml`
- `res/menu/log_item_detail_menu.xml`

Kept: strings (`log_detail_title`, `field_*`, `menu_log_detail_copy`, `copied`),
`baseline_content_copy_24`, `AppTheme.TextAppearance.Label/Value` (still used by
`item_http_detail_overview.xml`), the manifest entry and `AppComponent.inject(LogItemDetailActivity)`.

## Out of scope

- Migrating `ImportLogActivity` (and deleting the bridge Activity).
- Moving other Activities into the nav graph (e.g. `HttpLogDetailActivity`, `SettingsActivity`).
- Changing the copy text format.

## Testing / verification

No new or updated unit tests (AGENTS.md). Verification:

1. `assembleDebug` succeeds; dependency check above (Compose UI 1.5.2, navigation-compose 2.7.7).
2. On the emulator:
   1. Tap a text log → detail page shows tag, time with millis, message; copy shows toast and
      clipboard holds the text.
   2. Back arrow and system back return to the list at the same scroll position, with filter
      and search text intact.
   3. Log with meta shows Meta section; log without meta hides it.
   4. A message containing `& ? / % #`, newlines and emoji renders exactly as logged.
   5. Toggle theme / rotate while on the detail page → still shown and populated.
   6. Import screen → tap a text log → bridge Activity shows the same UI; back arrow finishes it.

## Changes from review (implementation notes)

Added during per-task code review; the code is the source of truth where this differs from the
sections above.

- **`VALUE_PREFIX` ("_") on every string route value**, stripped in `read()`. navigation 2.7.7's
  `NavType.StringType.parseValue("null")` returns real null, which crashed navigation for a log whose
  tag/message is literally `"null"`; values equal to their own `{placeholder}` were also dropped.
- **Long values are chunked** (`chunkForDisplay`, `MAX_CHARS_PER_TEXT = 1000`, newline-preferring,
  surrogate-safe, exact concatenation) into one `Text` per chunk. Foundation 1.5.2 throws
  ("Can't represent a width … and height … in Constraints") for a single Text taller than ~262k px
  (~5,000 lines). Boundary newlines are not drawn twice; a manual selection spanning a boundary may
  lose that newline (the Copy action is exact).
- **Back arrow**: content description `R.string.navigate_up` (new string), mirrored in RTL via
  `graphicsLayer { scaleX = -1f }` (no `Icons.AutoMirrored` in this Compose version).
- **Labels wrapped in `DisableSelection`** so only values are selectable, as in the XML.
- **Double-tap guard** on text-log taps: navigate only when the current back-stack entry is RESUMED.
- **Back uses `popBackStack(ROUTE_LOGS, inclusive = false)`**: plain `popBackStack()` popped the start
  destination on a second tap during the exit fade, leaving an empty NavHost.
- `LogActivity`'s NavHost is split into private member composables `LogNavHost` /
  `LogsDestination` / `LogDetailDestination`.
- **Accepted trade-off — saved-state size**: `navigate(route)` stores the decoded args plus the full
  percent-encoded route (`KEY_DEEP_LINK_INTENT`) in the back stack, so a very large non-ASCII message
  hits the ~1 MB saved-state limit sooner than the old Intent extras did (crash on backgrounding).
  Gate: on-device check with a ~100k-char non-ASCII message + Home.

### Post-device-test change: Bundle navigation by destination id

On-device check 9 found that tapping a ~50k-character (non-ASCII) log crashed with
`IllegalArgumentException: Navigation destination that matches request NavDeepLinkRequest{…} cannot be found`
— navigation 2.7.7 regex-matches the route URI and fails on very long URIs (5k chars worked).

Decision 3 is revised: values are passed as a **Bundle by destination id**
(`LogDetailArgs.navigate(navController, data)` → `navController.navigate(graph.findNode(ROUTE_PATTERN).id, bundleOf(...))`).
`ROUTE_PATTERN` and `arguments` are kept only to register the destination (a required `timestamp` must
appear in the route's deep link or graph construction throws). Consequences:
- No URI encoding and no `VALUE_PREFIX` — Bundle args are never string-parsed, so a literal `"null"` is safe.
- No `KEY_DEEP_LINK_INTENT` copy in saved state; the saved-state ceiling is now the same as the old Intent
  extras (~400–500k chars before `TransactionTooLargeException` on backgrounding). Long-term fix: pass a log id.
- Back-stack args are still restored after process death.
- Callers must never use `navController.navigate(String)` for this destination.
