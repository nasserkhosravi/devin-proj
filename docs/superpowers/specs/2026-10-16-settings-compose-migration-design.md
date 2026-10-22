# SettingsActivity → Jetpack Compose migration — design

Date: 2026-10-16
Branch: `compose-migration` (one commit per screen)

## Context

`present/settings/SettingsActivity.kt` (XML `activity_settings.xml`, view binding) was added in presenter
4.7.0 and is opened from `LogActivity`'s overflow menu via `SettingsActivity.intent(context)`. It shows:

- Toolbar "Settings" with a navigation (back) icon → `finish()`.
- **Storage** section: `MaterialSwitch` "Auto-delete old logs" (+ summary) bound to `appPref.retentionEnabled`;
  rows "Keep logs for" (value `formatDays(appPref.retentionDays)`) and "Max logs per app"
  (`formatCount(appPref.retentionMaxCount)`), each opening a single-choice picker
  (`RetentionPolicy.AGE_OPTIONS_DAYS` / `COUNT_OPTIONS`); rows look disabled (alpha 0.38, not clickable)
  when retention is off. Any change calls `retentionObserver.resetThrottle()`.
- Divider, "Storage used" row: `StorageUsage.computeBytes(applicationContext)` on IO, formatted with
  `Formatter.formatShortFileSize`, "Calculating…" while loading, "—" on failure.
- Divider, **Pinned filter tags** section (+ summary): one chip per `filterRepository.getPinnedTags()`
  with a close icon (content description `settings_pinned_tags_remove`) → `unpinTag(tag)`; a final
  "Add tag" chip → dialog with a single-line text field (hint `settings_pinned_tags_add_hint`),
  OK → `pinTag(text)`, Cancel.

## Decisions (made autonomously per the user's "migrate all screens" instruction)

1. **Stay a standalone Activity** with `setContent { DevinTheme { SettingsScreen(...) } }` — it is a
   separate Activity reached by Intent; the `intent(context)` API, Dagger injection and manifest entry
   stay. Same shape as the migrated `StarterActivity`.
2. **Activity is the state holder** (no new ViewModel): Compose `mutableStateOf` fields for
   `retentionEnabled`, `retentionDays`, `retentionMaxCount`, `pinnedTags`; storage text via
   `produceState` on IO inside the screen's host composable (or a `LaunchedEffect` writing a state field).
   Writes go straight to `appPref` / `filterRepository` exactly as today, then refresh the state.
3. **Pickers and the add-tag dialog become Material3 `AlertDialog` composables** controlled by local
   state (which dialog is open is `rememberSaveable` so it survives rotation; the add-tag text field uses
   local `rememberSaveable` text). Picker = list of `RadioButton` rows, tapping a row picks and closes
   (same as `setSingleChoiceItems` + dismiss). No confirm button, Cancel via outside tap/back.
4. **Chips**: `FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`, foundation 1.5.2) of Material3
   `InputChip` (`@OptIn(ExperimentalMaterial3Api::class)`) with a trailing close `Icon(Icons.Default.Close)`
   whose click unpins; plus an `AssistChip` "Add tag" with `Icons.Default.Add` leading icon (check both
   icons exist in material-icons-core 1.5.x; `Close` does, `Add` does).
5. Back arrow `Icons.Default.ArrowBack` with `R.string.navigate_up`, mirrored in RTL like
   `LogItemDetailScreen` (reuse the same approach).

## Components

- `present/settings/SettingsScreen.kt` (new): stateless `SettingsScreen(state, callbacks…)` +
  private section composables (`SectionHeader`, `SwitchRow` with summary, `ValueRow` with disabled
  look, `StorageRow`, `PinnedTagsSection`), `RetentionPickerDialog`, `AddPinnedTagDialog`. Scrollable
  `Column` (verticalScroll) in a `Scaffold` with `TopAppBar`. Match the XML's spacing/typography
  (read `activity_settings.xml` and its styles for section header color/size, paddings, divider insets);
  use `Divider` (Material3 1.1.2 has no `HorizontalDivider`), `Switch` for MaterialSwitch.
- `SettingsActivity.kt` (rewritten): injection unchanged; holds state; implements callbacks
  (toggle retention, pick days/count, unpin, pin); keeps `formatDays` (plurals) / `formatCount` /
  storage formatting helpers; `companion object { fun intent(context) }` unchanged.
- Delete `res/layout/activity_settings.xml` (grep first). Keep strings/plurals.

## Pinning behaviour parity

`FilterRepository.pinTag(text)` is called with the raw field text as today (read `pinTag` — if it
already trims/ignores blanks, keep the OK button always enabled; if not, still keep behaviour identical
and don't add validation).

## Verification

Build + on-device: toggle switch (rows enable/disable look, persisted after reopening); pick keep-days
and max-count (values update, persisted); storage value appears; unpin a tag (chip disappears and the
tag is no longer pinned in the log filter bar); add a tag via dialog (appears); rotate with a picker /
the add dialog open (stays open, typed text kept); back arrow and system back finish the Activity;
dark mode.
