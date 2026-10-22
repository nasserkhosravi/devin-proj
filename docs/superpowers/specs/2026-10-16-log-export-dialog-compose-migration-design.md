# LogExportDialog → Jetpack Compose migration — design

Date: 2026-10-16
Branch: `compose-migration` (single branch for all remaining screens, one commit per screen; cut from
`compose-log-item-detail` @ 4a74783)

## Context

`LogExportDialog` is a `BaseDialog` (`DialogFragment`) with XML `dialog_log_export.xml`, opened only from
`LogActivity.showExportDialog()` via `supportFragmentManager`. It offers:

- Mode: **Default** (explanation text `msg_default_export_explain`; exports a zip with an index file and
  per-tag files) or **Builder** (tag whitelist field, "day filter" number field defaulting to `1`,
  "Separate tag files" checkbox).
- **Save Logs**: prepare the export file → system "create document" picker
  (`ExportViewModel.createIntentForSave(zip)`) → copy the temp file to the picked Uri → toast
  `msg_save_done` / `error_msg_something_went_wrong` → dismiss. Picker cancel: nothing happens.
- **Share Logs**: prepare → share chooser (`sendOrShareFileIntent`, FileProvider) → dismiss.
- Indeterminate progress over the content while exporting.

All flow state (job, temp file) lives in the dialog's fields, so if the Activity is recreated while the
save picker is open the temp file reference is lost and the save is silently dropped (bug).

## Decisions (approved by the user)

1. **Navigation `dialog()` destination** in `LogActivity`'s `NavHost` (route `ROUTE_EXPORT = "export"`),
   consistent with the log-detail page. Back/outside tap dismiss via navigation; the dialog survives
   rotation because it's on the back stack.
2. **`ExportViewModel` scoped to that back-stack entry** via
   `viewModel(factory = vmFactory)` inside the `dialog {}` destination (cleared when the dialog closes).
3. **Export flow state and jobs move into `ExportViewModel`** (survive rotation; fixes the lost-save bug).

## Bugs fixed along the way (call out in the commit)

- Share always used `MIME_APP_JSON`, even for the default zip export. Use `MIME_APP_ZIP` when the
  prepared file is a zip (`withSeparationTagFiles == true`), else `MIME_APP_JSON`.
- The days field could crash on negative input ("-1" → `PositiveNumber` require). Input is filtered to
  digits and negative values are ignored.

**Not fixed here (separate task):** the day filter is semantically inverted — the bound uses
`inWholeMicroseconds` on a millisecond timestamp *and* the DAO selects `date < bound` (logs older than N
days). Fixing it needs a DAO change and an update to `ContentProviderLogsDaoTest`, which requires the
user's approval, so this migration keeps the existing (broken) behaviour unchanged.

## Components

### `ExportViewModel` (changed)

New state/events (existing helpers stay as building blocks; `Common` and `ReaderViewModel`'s
single-filter share unchanged):

```kotlin
val isExporting: StateFlow<Boolean>
val events: Flow<ExportEvent>   // Channel(BUFFERED).receiveAsFlow(): delivered once

sealed interface ExportEvent {
    data class LaunchSavePicker(val intent: Intent) : ExportEvent
    data class Share(val uri: Uri, val mimeType: String) : ExportEvent
    data class Message(@StringRes val textRes: Int) : ExportEvent
    data object Close : ExportEvent   // Kotlin 1.9: data object is fine
}

fun startSave(options: ExportOptions)            // prepare → keep temp file → LaunchSavePicker(createIntentForSave(zip))
fun onSaveLocationPicked(uri: Uri?)              // null = cancelled: delete temp file, stop progress, stay open
                                                 // else copy → Message(done|error) → Close
fun startShare(options: ExportOptions)           // prepare → Share(fileProviderUri, mime) → Close
override fun onCleared()                         // cancel job, delete temp file
```

- One `private var job: Job?`; starting any action cancels the previous one and deletes a stale temp file.
- Errors while preparing (exception in the flow) → `Message(error_msg_something_went_wrong)`, progress off,
  dialog stays open. (Today an exception would crash/leave the spinner; this is the safe equivalent.)
- `zip` for save/share is `options.withSeparationTagFiles` (Default option has it `true`; Builder uses
  the checkbox) — same as today's `needZipFile()`.
- FileProvider Uri built with the application context (`toUriByFileProvider`).

### `LogExportDialog.kt` (rewritten)

- `@Composable fun LogExportRoute(viewModel: ExportViewModel, onClose: () -> Unit)`:
  - form state via `rememberSaveable`: `isBuilder` (false), `tagWhitelist` (""), `days` ("1"),
    `separateTagFiles` (false). Plain local state, no debounce.
  - collects `isExporting` (`collectAsState`) and `events` in a `LaunchedEffect(viewModel)`:
    `LaunchSavePicker` → `rememberLauncherForActivityResult(StartActivityForResult)` launching
    `Intent.createChooser(intent, getString(R.string.menu_export_logs))`, result →
    `viewModel.onSaveLocationPicked(if RESULT_OK) data?.data else null)`;
    `Share` → `startActivity(Intent.createChooser(sendOrShareFileIntent(uri, mime), title_of_share))`;
    `Message` → Toast LONG; `Close` → `onClose()`.
  - builds `ExportOptions` exactly like today: Default → `viewModel.getExportDefaultOption()`; Builder →
    `viewModel.buildExportCustom(tagWhitelist, days.toIntOrNull(), separateTagFiles)`.
- `@Composable fun LogExportDialogContent(...)` stateless UI inside a `Surface` (shape
  `MaterialTheme.shapes.extraLarge`, padding `spacing.small` like the XML): `Row` of two
  `RadioButton`+label ("Default", "Builder" — same literal labels as XML), then either the explanation
  `Text` or the Builder `Column` (`OutlinedTextField` whitelist, `OutlinedTextField` days with
  `KeyboardType.Number`, `Checkbox` row), then full-width `Button`s Save / Share. While exporting:
  buttons disabled and a centred `CircularProgressIndicator` overlay (Box). Keep the content area height
  stable when toggling modes (XML used `invisible`, not `gone`) — acceptable to let it change; do NOT add
  fixed heights.
- Reuse the `dialog()` destination's built-in window; no extra `Dialog {}`.

### `LogActivity` (changed)

- `LogNavHost`: add `dialog(ROUTE_EXPORT) { val vm: ExportViewModel = viewModel(factory = vmFactory); LogExportRoute(vm, onClose = { navController.popBackStack(ROUTE_LOGS, inclusive = false) }) }`.
- `LogsDestination`: `onExport = { if (RESUMED guard) navController.navigate(ROUTE_EXPORT) }` (same guard
  helper/pattern as `onTextLogClick`). Remove `showExportDialog()`.
- `private const val ROUTE_EXPORT = "export"`.

### Removed

- `res/layout/dialog_log_export.xml`; `AppComponent.inject(dialog: LogExportDialog)`.
- `arch/BaseDialog.kt`, `arch/BaseDialogCommon.kt` **only if** grep confirms no other user (currently
  only LogExportDialog uses them). `R.style.DialogTheme` only if unused elsewhere.

## Dependencies

- `androidx.lifecycle:lifecycle-viewmodel-compose` — already transitively on the classpath via
  navigation-compose 2.7.7 (2.6.2). Declare it explicitly in the catalog at the resolved version
  (2.6.2) so `viewModel()` usage isn't relying on a transitive dep. Verify Compose UI still resolves 1.5.2.

## Out of scope

- `ReaderViewModel` single-filter share; export file formats; strings.

## Verification

No new tests (AGENTS.md). Build + on-device:
1. Default → Save → picker → save → "Save done", file is a zip; Default → Share → share sheet (zip type).
2. Builder with tag `a,b`, days `1`, separate off → Save produces a `.json`; with separate on → `.zip`.
3. Days field accepts digits only; "-1" can't be entered (no crash).
4. Save picker cancel → dialog stays open, no toast, no spinner.
5. Open save picker, rotate (or toggle dark mode), then save → file saved (lost-save bug fixed).
6. Dismiss (back) while exporting → no crash, no toast afterwards.
7. Type into both fields; text survives rotation.
