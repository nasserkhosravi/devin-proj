# present-app end-to-end (instrumented) tests — design

Date: 2026-10-20
Branch: `compose-migration` (Compose build only; the user decided not to run these against `main`)
Source of behaviours: `docs/superpowers/specs/2026-10-20-presenter-behaviour-inventory.md` (IDs below)

## Goal

Characterization-style e2e tests that pin the presenter's user-visible behaviour (as inventoried from
`main`, plus approved intentional changes) on the Compose build, so regressions are caught by CI/device
runs rather than manual checks.

## Decisions

1. **Location / runner**: `present-app/src/androidTest/java/com/khosravi/devin/present/e2e/`, JUnit4,
   `AndroidJUnitRunner` (already configured). **Android Test Orchestrator** with
   `clearPackageData=true` (via `testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"` +
   `testInstrumentationRunnerArguments["clearPackageData"] = "true"` + `androidTestUtil` orchestrator) so
   every test starts with an empty DB and prefs.
2. **Framework**: Compose UI testing — `createAndroidComposeRule<SomeActivity>()` / `createEmptyComposeRule()`
   + `ActivityScenario`, finding nodes by **visible text / content description** (black-box; add
   `Modifier.testTag` only where text is ambiguous, and keep tags minimal). Cross-activity flows use
   `createEmptyComposeRule()` + `ActivityScenario.launch(StarterActivity)` (the compose rule sees any
   resumed Compose activity).
3. **System UI**: **Espresso-Intents** (`Intents.init/release`, `intending(...).respondWith(...)`,
   `intended(...)`) — stub `ACTION_CREATE_DOCUMENT` (export save, HAR save) with a result pointing at a
   file the test owns (e.g. a `FileProvider`/`content://` Uri for a file in the test app's cache, or
   `Uri.fromFile` if the app's `contentResolver.openOutputStream` accepts it) and then assert the file's
   bytes; stub `ACTION_SEND` / chooser and assert extras/MIME; stub `ACTION_VIEW` (release page).
   No real DocumentsUI / share sheet / browser.
4. **Test data**: seed through the app's own content provider (`DevinUriHelper` / `DevinContentProvider`
   helpers: client row with optional `presenterConfig` JSON incl. `logPassword`; log rows with
   `_clientId/_tag/_value/_date/_meta/_typeId`; HTTP logs via the same insert the OkHttp interceptor
   uses — reuse `devin-write-okhttp`/HAR builders or a captured HAR JSON fixture in androidTest assets).
   A small `TestData` helper object in androidTest. Fixed `_date` values (e.g. millis ending in `123`) so
   times are assertable.
5. **Determinism**:
   - Suppress the GitHub update check: before launching, write `AppPref` `lastUpdateCheckAt = now`
     (via `AppPref` API or the `AppPref` SharedPreferences file) so `checkInBackgroundIfDue` is a no-op;
     update-dialog tests seed the cached latest-release prefs instead (`saveUpdateCheckResult`) to drive
     optional/force dialogs without network.
   - Grant/deny POST_NOTIFICATIONS deterministically (`GrantPermissionRule` or `UiAutomation`
     `revokeRuntimePermission`) so the permission dialog never appears.
   - Wait with `composeTestRule.waitUntil(timeoutMillis) { onAllNodes(...).fetchSemanticsNodes().isNotEmpty() }`
     for async loads (provider queries, 700 ms search debounce); never fixed sleeps except where the app
     itself delays.
   - Disable animations (`testOptions.animationsDisabled = true`).
6. **Dependencies** (pinned to the Compose BOM 2023.09.02 / existing stack; no version bumps of
   app deps): `androidx.compose.ui:ui-test-junit4` (BOM) as `androidTestImplementation`,
   `androidx.compose.ui:ui-test-manifest` (BOM) as `debugImplementation`, `androidx.test:runner`,
   `androidx.test:rules`, `androidx.test.ext:junit`, `androidx.test.espresso:espresso-intents`,
   `androidx.test:orchestrator` (`androidTestUtil`). Pick versions compatible with minSdk/targetSdk and
   AGP 8.10 (e.g. runner 1.5.2, rules 1.5.0, ext-junit 1.1.5, espresso 3.5.1, orchestrator 1.4.2); add
   catalog entries.

## Coverage (inventory priority order; each test names its IDs)

1. ROW-04 time with millis; ROW-01/02/03/09/11/13 each row type's text (level badge text incl.
   "VERBOSE"→"Verbose", tag chip, HTTP status/method/path/host, session-start, date header, image row).
2. LIST-17 custom filter titled like an existing tag → no crash, chip appears.
3. Password gate LOGIN-01/04/06/07/08 + LOGIN-02 alphanumeric password accepted; wrong → inline error;
   4 wrong → toast + locked; remembered on next open.
4. Starter START-02/03/04/07: zero clients message, one client auto-route, multi-client list, footer.
5. LIST-06/20/21/22/23: filter selection, search (debounce), search across pages (≥41 logs), clear (×).
6. ROW-05 tag chip visible on All/custom, hidden on a tag chip.
7. NOTIF-02 launch Starter with notification extras (client id + tag) → log list with that tag selected.
8. DET-01/02/04 + LIST-32: detail content (tag, time with millis, message, meta), copy text format
   (clipboard), back keeps list (scroll position assertion via first visible item).
9. LIST-09..13 + SET-07: chip long-press menu (pin/unpin/share/delete), pin persists across relaunch,
   pinned tag appears in Settings.
10. LIST-25/26/19: refresh, clear logs (provider empty), clear custom filters.
11. EXP-04/05/06/07/11: export Default → save (stubbed CREATE_DOCUMENT) writes a zip with expected
    entries; Builder with tag filter + separate off → JSON; share → ACTION_SEND with correct MIME.
12. HTTP-03/05/06/08/10/11/14..21: open seeded HTTP log; tabs; overview fields; literal search ("..",
    `{"`) counter "i / n"; copy cURL/HAR text; save HAR (stubbed) content.
13. SET-02..06/11: retention switch/pickers persist across relaunch; storage text shown.
14. UPD-01/03..06 with seeded release prefs: optional prompt Later/Download (Download fires ACTION_VIEW
    and does NOT start LogActivity), force dialog blocks routing, back finishes.
Import viewer: not reachable from UI — test by launching `ImportLogActivity.intent(context, uri)` with a
fixture file Uri (IMPORT-xx): renders rows, search filters, tap → detail, empty/invalid file → toast+finish.

## Non-goals
Screenshot tests (later, Roborazzi); running against `main`; testing DocumentsUI/Chrome themselves.

## How to run
`./gradlew :present-app:connectedDebugAndroidTest` with exactly one device/emulator
(`ANDROID_SERIAL=emulator-5554`). Document this in the test package README comment or AGENTS.md
Gotchas (only if the user agrees to edit AGENTS.md — otherwise a short `README.md` in the e2e package).
