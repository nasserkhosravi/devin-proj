# Presenter app — behaviour inventory (characterization spec)

Date: 2026-10-20
Branch: `compose-migration`

## Purpose

An as-is functional spec of the presenter app (`present-app`), taken from the **old View/XML/FastAdapter UI**
and mapped onto the **new Jetpack Compose UI**. Use it to write black-box end-to-end tests and to find
regressions the migration may have caused. Only user-observable behaviour is listed: what is on screen, toasts,
dialogs, navigation, what is saved, copied or shared, and persisted settings.

## Sources

- **Old:** `origin/main` (presenter 4.6.0). `e5ad343` (`presenter-release-4.7.0` before the Compose merge) was
  also checked: under `present-app/src/main` it is **byte-identical** to `origin/main` (`diff -rq`: only
  `src/test/.../FilterRepositoryTest.kt` differs). So the "4.7.0-only" features (full-page log detail, Settings,
  update check, version footer, pinned tags) are already on `origin/main`, and every `Old:` reference is valid
  for both refs.
- **New:** worktree `.worktrees/compose-migration`, HEAD `72bd92e`.
- **Intentional changes:** `docs/superpowers/specs/2026-10-15…2026-10-17-*.md`, the earlier migration specs
  `2026-08-28/29/30-*.md` (FilterDialog, Starter, ClientLoginBottomSheet, LogActivity), and the commit messages
  on `compose-migration` (`2848a5e`, `f81852a`, `74d56f4`, `ee484d6`, `a288209`, `4a74783`, `7b210a3`,
  `858ddd6`, `72bd92e`).

## How to read

- **IDs** are `AREA-NN` (`START`, `LOGIN`, `UPD`, `LIST`, `ROW`, `DET`, `EXP`, `SET`, `IMP`, `HTTP`, `NOTIF`,
  `GEN`). They are stable: add new items at the end of a section and don't renumber.
- **Paths:** `P/` = `present-app/src/main/java/com/khosravi/devin/present/`, `R/` = `present-app/src/main/res/`.
  `Old:` paths are in `origin/main`. `New:` paths are in `compose-migration`. Lines are 1-based.
- **Status:**
  - `SAME`: the user sees the same behaviour.
  - `CHANGED-INTENTIONALLY`: a spec or commit documents the change. The citation and the new behaviour follow.
  - `CHANGED-UNDOCUMENTED`: the behaviour differs, but no spec or commit mentions it. These need a decision:
    some are bug fixes, some are regressions. This status was added to the four requested ones so that
    undocumented differences don't end up filed as `SAME`.
  - `MISSING-IN-NEW`: the old behaviour has no counterpart in the new code (checked by searching the whole
    `present-app` module).
  - `NEW-ONLY`: the old code has no counterpart.
- **E2E:** `yes`/`no`, plus how a black-box test can check it.

### Test data (E2E)

The presenter hosts the shared provider (`com.khosravi.devin.provider`, exported). A client becomes visible
when it has a row in the **client** table. Logs go into the **log** table:

```sh
adb shell content insert --uri content://com.khosravi.devin.provider/client \
  --bind _clientId:s:e2e.client --bind _presenterConfig:s:'{}'
adb shell content insert --uri content://com.khosravi.devin.provider/log \
  --bind _clientId:s:e2e.client --bind _tag:s:Net --bind _value:s:hello \
  --bind _date:l:1760950000123 --bind _meta:s:'{"log_level":6}'
```

- `log_level` uses `android.util.Log` values: 2 = Verbose, 3 = Debug (the default when it is missing),
  4 = Info, 5 = Warn, 6 = Error.
- Session start row: `_tag=SessionStart`, `_meta={"payload":"App Version: 1.2.3 (45)"}`.
- Image row: `_typeId=dvn_image`, `_value=<name>`, `_meta={"url":"https://…","status":1}`. Status 1 means
  succeeded and 2 means failed.
- HTTP rows (`_typeId=dvn_okhttp`, tag `okhttp`) need a HAR meta blob and a `url` meta index. Generating them
  with `sample-app`'s OkHttp screen is easier.
- `presenterConfig` keys:
  - `logPassword`: password gate. `sample-app` uses `12346`.
  - `logNotifications`: `{enabled, groups:[{name, tags, color}]}`.
- Logs are listed newest first, 40 per page.
- Date headers use the **Persian (Jalali)** calendar (`AppModule.calendarProxy()` = `CalendarType.PERSIAN`),
  formatted `yyyy-M-d` without zero padding. Times are local `HH:mm:ss`.

---

## 1. Starter (client selection)

**START-01 — Initial screen**
- Given the app is launched, When the Starter screen appears, Then the top bar shows "Client selection" with a
  refresh icon (content description "Refresh"). The message reads "loading" (bold) until the client list loads.
  A footer is at the bottom (see UPD-01).
- Old: `R/layout/activity_starter.xml:10-28`; `P/present/StarterActivity.kt:202-215`
- New: `P/present/StarterScreen.kt:55-73`; `P/present/StarterActivity.kt:167-180`
- Status: SAME
- E2E: yes. Launch and assert "Client selection" and the Refresh button. "loading" shows for only about 100 ms,
  so don't assert it.

**START-02 — No clients**
- Given the client table is empty, When the list loads, Then the message is "No client data found, run client
  app or send logs to devin" and no rows are shown.
- Old: `P/present/StarterActivity.kt:272-278,310-312`
- New: `P/present/StarterActivity.kt:209-215,240-243`
- Status: SAME
- E2E: yes. Clear the app data, launch, and assert the text.

**START-03 — One client: auto-route**
- Given exactly one client, When the list loads, Then the message is "One client found" and the client's package
  id is listed. The app opens that client's log list without a tap (unless a password or an update prompt
  intervenes: LOGIN-01, UPD-04, UPD-06).
- Old: `P/present/StarterActivity.kt:284-297,217-223`
- New: `P/present/StarterActivity.kt:221-232,182-188`
- Status: SAME
- E2E: yes. Insert one client and assert that the log list (title "Devin", chip "All") appears.

**START-04 — Several clients**
- Given two or more clients, When the list loads, Then the message is "Choose client source" and one row per
  package id is shown, with dividers. There is no auto-route.
- Old: `P/present/StarterActivity.kt:299-308`
- New: `P/present/StarterActivity.kt:234-238`; `P/present/StarterScreen.kt:74-86`
- Status: SAME
- E2E: yes. Insert two clients and assert both ids and the message.

**START-05 — Tap a client**
- Given the client list, When a row is tapped, Then that client is selected. Its log list opens directly, or
  after the password sheet if one is required.
- Old: `P/present/StarterActivity.kt:86-89,217-223,316-328`
- New: `P/present/StarterScreen.kt:81`; `P/present/StarterActivity.kt:182-188,247-259`
- Status: SAME
- E2E: yes.

**START-06 — Refresh**
- When refresh is tapped, Then the list is fetched again and START-02/03/04 apply again. With one client this
  auto-routes again.
- Old: `P/present/StarterActivity.kt:256-269`
- New: `P/present/StarterScreen.kt:60`; `P/present/StarterActivity.kt:205-207`
- Status: SAME
- E2E: yes. Add a second client while on Starter, refresh, and assert both rows.

**START-07 — Back from the log list**
- Given the app auto-routed to the log list, When the user presses back, Then the Starter screen is shown ("One
  client found" plus the row) and it does not auto-route again.
- Old/New: routing only runs after a fetch in `onCreate`/`onNewIntent`/refresh (Old
  `StarterActivity.kt:91,195-200`; New `StarterActivity.kt:90,160-165`).
- Status: SAME
- E2E: yes.

**START-08 — Rotation on Starter**
- When Starter is rotated, Then the list is fetched again. With one client the app auto-routes to the log list
  again.
- Old: `P/present/StarterActivity.kt:78-93`
- New: `P/present/StarterActivity.kt:64-91`
- Status: SAME
- E2E: yes (`adb shell settings put system user_rotation 1`).

**START-09 — Zero state after clients disappear**
- Given rows were shown, When a refresh returns zero clients, Then the old screen kept the stale rows under the
  "No client data found…" message. The new screen clears them.
- Old: `P/present/StarterActivity.kt:310-312` (the adapter is not cleared)
- New: `P/present/StarterActivity.kt:240-243` (`clients = emptyList()`)
- Status: CHANGED-UNDOCUMENTED (bug fix)
- E2E: yes. Delete the client rows through the provider, refresh, and assert that no rows remain.

**START-10 — Notification permission prompt**
- Given Android 13+ and any client whose `presenterConfig.logNotifications.enabled` is true, When the list loads
  and POST_NOTIFICATIONS is not granted, Then the system permission dialog is shown once per Activity instance.
  Routing continues after the user answers.
- Old/New: `P/notification/LogNotificationLaunchCoordinator.kt:36-47` (identical);
  `StarterActivity.kt:210` (old) / `:175` (new)
- Status: SAME
- E2E: yes. Use a client whose config enables notifications, and run `pm revoke` first. This involves system UI.

## 2. Client login sheet

**LOGIN-01 — Password sheet shown**
- Given a client whose `presenterConfig.logPassword` is set and that hasn't been confirmed, When it is selected,
  Then a bottom sheet appears with the title "Enter Numeric Password", a field labelled "Password" (masked), and
  a full-width "Confirm" button.
- Old: `P/domain/ClientLoginInteractor.kt:15-67`; `R/layout/fragment_client_login.xml:10-43`
- New: `P/domain/ClientLoginInteractor.kt:13-60`; `P/present/ClientLoginBottomSheet.kt:66-98`
- Status: SAME
- E2E: yes. Use the config `{"logPassword":"12346"}`.

**LOGIN-02 — Keyboard type**
- Old: `inputType="textPassword"`, which gives a full alphanumeric keyboard. This was an explicit 4.3.1 change
  (commit `1cf25cf`: "Change password field inputType to textPassword").
- New: `KeyboardType.Password`, a full alphanumeric keyboard, masked, with `ImeAction.Done`
  (`P/present/ClientLoginBottomSheet.kt:78`).
- Old: `R/layout/fragment_client_login.xml:32`
- Status: SAME (fixed). Commit `858ddd6` had switched it to `KeyboardType.NumberPassword` on the mistaken belief
  that the original XML used `numberPassword`; that was reverted to match `origin/main` (`textPassword`, commit
  `1cf25cf`), so alphanumeric `logPassword` values can be typed again.
- E2E: yes. Use the password `"ab12"`, tap the field, and check that a QWERTY keyboard is available. This
  involves system UI (IME).

**LOGIN-03 — Show/hide password**
- Old: the eye-icon toggle of `TextInputLayout` (`passwordToggleEnabled`).
- New: a trailing "SHOW"/"HIDE" text button (`P/present/ClientLoginBottomSheet.kt:80-84`).
- Old: `R/layout/fragment_client_login.xml:24`
- Status: CHANGED-INTENTIONALLY (`2026-08-29-client-login-bottomsheet-compose-migration-design.md`: the icon
  isn't in material-icons-core)
- E2E: yes. Tap "SHOW" and assert that the typed digits are visible.

**LOGIN-04 — Wrong password**
- When a wrong password is confirmed, Then the inline error "Incorrect password. Please try again." is shown and
  the sheet stays open. Editing the field clears the error.
- Old: `P/present/ClientLoginBottomSheet.kt:64-79`
- New: `P/present/ClientLoginBottomSheet.kt:49-64,71-76`
- Status: SAME
- E2E: yes.

**LOGIN-05 — IME Done submits**
- When Done is pressed on the keyboard, Then the result is the same as tapping Confirm.
- Old: `P/present/ClientLoginBottomSheet.kt:53-60`
- New: `P/present/ClientLoginBottomSheet.kt:78-79`
- Status: SAME
- E2E: yes (`adb shell input keyevent 66`).

**LOGIN-06 — Correct password**
- When the correct password is confirmed, Then the sheet closes and the client's log list opens. The password is
  remembered: later selections of the client skip the sheet until its `logPassword` changes.
- Old: `P/domain/ClientLoginInteractor.kt:19-25,49-54`
- New: `P/domain/ClientLoginInteractor.kt:17-23,42-47`
- Status: SAME
- E2E: yes. Log in, go back, tap the client again, and assert that no sheet appears.

**LOGIN-07 — Too many attempts**
- When the 4th wrong password is confirmed, Then the sheet closes and the toast "Too many wrong password try"
  (long) is shown. No log list opens. (New: the toast may appear while the sheet is still sliding away.)
- Old: `P/domain/ClientLoginInteractor.kt:27-36,69-71`
- New: `P/domain/ClientLoginInteractor.kt:24-32,62-64`
- Status: SAME
- E2E: yes. Read the toast text with a UiAutomator toast watcher, or skip the toast and assert that the list
  didn't open.

**LOGIN-08 — Locked out for 8 hours**
- Given 4 wrong attempts within the last 8 hours, When the client is selected, Then the toast "Too many wrong
  password try" is shown at once and no sheet appears. After 8 hours the counter resets.
- Old/New: `P/data/AppPref.kt:76-90` (identical); `ClientLoginInteractor.kt:57-61` (old) / `:50-54` (new)
- Status: SAME
- E2E: yes, for the lockout half. The reset half needs clock manipulation.

**LOGIN-09 — Dismiss without submitting**
- When the sheet is swiped down, the outside is tapped, or back is pressed, Then the sheet closes and the user
  stays on Starter.
- Old: `BottomSheetDialogFragment` default
- New: `P/present/ClientLoginBottomSheet.kt:66` (`onDismissRequest = close()`)
- Status: SAME
- E2E: yes.

**LOGIN-10 — Rotation with the sheet open**
- Old: the `DialogFragment` was recreated, but its listener (a field that isn't restored) was lost, so a correct
  password closed it without routing. Starter's refetch then re-prompted for one client.
- New: the sheet lives in a transient `ComposeView` and disappears on rotation. One client gets the sheet again
  via the refetch.
- Old: `P/present/ClientLoginBottomSheet.kt:19,96-98`
- New: `P/present/ClientLoginDialogHost.kt:10-30`
- Status: CHANGED-UNDOCUMENTED (edge case)
- E2E: no (low value).

## 3. Update check, dialogs, footer

**UPD-01 — Footer, no update**
- Then the footer reads "v<versionName> by nasser.khosravi", centred, in the normal colour. Tapping it opens
  `https://github.com/nasserkhosravi/devin-proj/releases` in a browser.
- Old: `P/present/StarterActivity.kt:113-129`
- New: `P/present/StarterActivity.kt:76-78,110-114`; `P/present/StarterScreen.kt:87-97`
- Status: SAME
- E2E: yes. Assert the text and that a browser intent fires (hermetic tests can use an intent monitor).

**UPD-02 — Footer, update available**
- Given a newer release is cached, Then the footer reads "v<cur> by nasser.khosravi · Update available: v<new>"
  in the update colour (red). Tapping it opens that release's page.
- Old: `P/present/StarterActivity.kt:115-128`
- New: `P/present/StarterScreen.kt:91`; `P/present/StarterActivity.kt:110-114`
- Status: SAME (the colour token moved to `DevinColors`, see GEN-02)
- E2E: partial. It needs a cached `latestReleaseVersion` in `AppPref`, either by seeding prefs (debug) or by a
  real newer GitHub release.

**UPD-03 — Optional update dialog**
- Given a newer release with the same major version and fewer than 3 prompts answered today, When Starter
  resumes, Then a dialog shows:
  - Title: "Devin update available".
  - Message: "Version X is available (you have Y)."
  - Buttons: "Download" and "Later".
- Any answer counts toward the daily limit: Later, back, outside tap, or Download.
- Old: `P/present/StarterActivity.kt:154-176`
- New: `P/present/StarterActivity.kt:122-141`; `P/present/StarterScreen.kt:112-119,122-143`
- Status: SAME
- E2E: partial (needs seeded prefs).

**UPD-04 — Optional prompt holds back auto-routing**
- Given one client (or a notification target) and an open optional prompt, Then routing to the log list waits.
  "Later", back, or outside tap closes the prompt and then routes.
- Old: `P/present/StarterActivity.kt:170-185`
- New: `P/present/StarterActivity.kt:128-150`
- Status: SAME
- E2E: partial.

**UPD-05 — Optional prompt: Download**
- Old: Download opened the release page and, because the dialog's dismiss listener ran the held-back route,
  also opened the log list underneath the browser.
- New: Download opens the release page and drops the held-back route. On return the user is on Starter.
- Old: `P/present/StarterActivity.kt:162-174`
- New: `P/present/StarterActivity.kt:134-139`
- Status: CHANGED-INTENTIONALLY (commit `858ddd6`)
- E2E: partial.

**UPD-06 — Force update (major version)**
- Given the cached release has a higher major version, Then a dialog shows:
  - Title: "Devin update required".
  - Message: "Version X is a major update and is required (you have Y)."
  - One button: "Download".
- An outside tap does not dismiss it. Back closes the app.
- It shows again on every resume (for example after returning from the browser).
- Selecting a client or a notification target does nothing while it is pending.
- Old: `P/present/StarterActivity.kt:131-152,187-193`
- New: `P/present/StarterScreen.kt:101-110`; `P/present/StarterActivity.kt:79-82,152-158`
- Status: SAME
- E2E: partial.

**UPD-07 — Force update while the optional prompt is open**
- Old: the force dialog stacked on top of the optional one.
- New: only the force dialog is drawn; the optional one waits behind it.
- Old: `P/present/StarterActivity.kt:97-103`
- New: `P/present/StarterScreen.kt:111-112`
- Status: CHANGED-UNDOCUMENTED (a code comment mentions it; the cleanup spec says "keeping the exact
  behaviour")
- E2E: no.

**UPD-08 — Update notification**
- Given notifications are allowed, When a check finds a new version, Then one system notification per version
  is posted. Its title and text are the same as the dialog's (UPD-03/06). Tapping it opens the release page.
  Checks run at most once per 24 hours, on Starter or log list resume.
- Old/New: `P/update/UpdateChecker.kt:43-74`; `P/update/UpdateNotifier.kt:25-52` (identical)
- Status: SAME
- E2E: no (needs the network/GitHub).

**UPD-09 — Force update while on the log list**
- Given the log list is open, When a force update becomes known, Then the log list closes and Starter (with the
  force dialog) is brought to the front.
- Old: `P/present/LogActivity.kt:456-468`
- New: `P/present/LogActivity.kt:314-326`
- Status: SAME
- E2E: partial.

**UPD-10 — No browser installed**
- When the footer or Download is tapped and no app handles ACTION_VIEW, Then the toast "Something went wrong!"
  (short) is shown.
- Old/New: `P/update/ReleasePage.kt:12-18` (identical)
- Status: SAME
- E2E: no.

**UPD-11 — Rotation with the optional prompt open**
- Then the prompt is shown again after recreation. An open prompt isn't counted toward the daily limit.
- Old: `AlertDialog` dismissed in `onDestroy`, re-shown from `onResume` (`StarterActivity.kt:95-105,240-249`)
- New: `P/present/StarterActivity.kt:116-126`
- Status: SAME
- E2E: partial.

## 4. Log list (LogActivity)

**LIST-01 — Top bar**
- Then the title is "Devin". It has these actions:
  - Clear-logs icon (content description "Clear all logs").
  - Refresh icon ("Refresh").
  - Overflow ("More options"). The old overflow was the system one.
- Old: `R/menu/main_menu.xml:5-15`; `P/present/LogActivity.kt:106,476-479`
- New: `P/present/LogScreen.kt:90-103`
- Status: SAME. The old Refresh was `showAsAction="ifRoom"`, so on very narrow screens it could fall into the
  overflow; the new one is always an icon.
- E2E: yes.

**LIST-02 — Overflow menu**
- When the overflow is opened, Then these items show, in this order:
  1. "Share/Save logs"
  2. "Create custom filter"
  3. "Clear custom filters"
  4. "Toggle theme"
  5. "Settings"
- ("Import logs as json" is commented out in both versions.)
- Old: `R/menu/main_menu.xml:17-42`; `P/present/LogActivity.kt:481-525`
- New: `P/present/LogScreen.kt:104-125`
- Status: SAME
- E2E: yes. Assert the 5 labels in order.

**LIST-03 — First load**
- When the screen opens, Then the "All" chip is selected and the client's logs are listed newest first (40 per
  page), with date headers (ROW-01).
- Old: `P/present/LogActivity.kt:144-156,275-320`; `P/present/ReaderViewModel.kt:307-312`
- New: `P/present/LogActivity.kt:102-109,190-210`; `P/present/ReaderViewModel.kt:351`
- Status: SAME
- E2E: yes. Insert 3 logs with known `_date` and assert their visual order.

**LIST-04 — Filter chips content and order**
- Then the chips are, in order:
  1. "All" (with a pin icon).
  2. Pinned items (with a pin icon).
  3. The remaining custom filters (by title) and the logged tags.
- The `okhttp` tag chip is labelled "http" and uses the dedicated okhttp colours (selected and unselected).
- Tags that already match a custom filter's id are not listed twice when the list is (re)built.
- Old: `P/filter/FilterItemViewHolder.kt:22-68`; `P/present/ReaderViewModel.kt:250-273`; `P/filter/IndexFilterItem.kt:9`
- New: `P/present/FilterChipsRow.kt:69-123`
- Status: SAME (colours: GEN-02)
- E2E: yes. Insert logs with tags `A` and `okhttp`, then assert the chips "All", "http", "A".

**LIST-05 — Chip row layout**
- Old: one horizontal row up to 4 chips. More than 4 switched to a 2-row horizontally scrolling staggered grid.
- New: always one horizontally scrolling row.
- Old: `P/present/LogActivity.kt:336-347`
- New: `P/present/FilterChipsRow.kt:54-64`
- Status: CHANGED-INTENTIONALLY (`2026-08-30-log-activity-compose-migration-design.md`, decision 2)
- E2E: yes (screenshot or bounds check with more than 4 tags).

**LIST-06 — Select a chip**
- When a chip is tapped (including the one already selected), Then it becomes selected and the list reloads:
  - All: every log.
  - Tag chip: logs whose tag **equals** the tag.
  - Custom filter: tag **contains** the criteria tag, and value **contains** the search text.
- Pagination resets and the search row is reset (LIST-21).
- Old: `P/present/LogActivity.kt:114-117,376-388`; `P/present/ReaderViewModel.kt:366-408`
- New: `P/present/FilterChipsRow.kt:108-112`; `P/present/LogActivity.kt:232-242`; `P/present/ReaderViewModel.kt:410`
- Status: SAME
- E2E: yes. Tags `A`/`B` with distinct values: tap `A` and assert that only A's values remain.

**LIST-07 — Chips while loading**
- Old: `rvFilter.isEnabled = false` during a load. `RecyclerView.setEnabled` doesn't block child clicks, so the
  chips stayed tappable.
- New: chips are disabled (`combinedClickable(enabled = …)`) from the tap until the result arrives.
- Old: `P/present/LogActivity.kt:298,377,528`
- New: `P/present/FilterChipsRow.kt:108-109`; `P/present/LogActivity.kt:197,233,304`
- Status: CHANGED-UNDOCUMENTED (minor)
- E2E: no (timing-dependent).

**LIST-08 — Long-press "All"**
- When "All" is long-pressed, Then nothing happens (no menu).
- Old: `P/present/LogActivity.kt:159`
- New: `P/present/FilterChipsRow.kt:96-100`
- Status: SAME
- E2E: yes.

**LIST-09 — Long-press a tag chip**
- Then a menu appears with "Pin" (or "Unpin" if pinned) and "Share as json". There is no "Remove".
- Old: `R/menu/menu_filter_item_quick_action.xml`; `P/present/LogActivity.kt:158-207`
- New: `P/present/FilterChipsRow.kt:125-142`
- Status: SAME
- E2E: yes.

**LIST-10 — Long-press a custom-filter chip**
- Then a menu appears with "Pin"/"Unpin" and "Remove". There is no "Share as json".
- Old: `P/present/LogActivity.kt:189-207`
- New: `P/present/FilterChipsRow.kt:125-148`
- Status: SAME
- E2E: yes.

**LIST-11 — Pin / Unpin**
- When Pin is chosen, Then the chip shows a pin icon and moves to the end of the pinned group. The pin persists:
  it survives a restart, and pinned tags appear in Settings → Pinned filter tags.
- Unpin removes the icon and moves the chip to the start of the unpinned group.
- Old: `P/present/LogActivity.kt:436-452`; `P/present/ReaderViewModel.kt:338-356`
- New: `P/present/LogActivity.kt:256-274`; `P/present/FilterChipsRow.kt:115-122`
- Status: SAME. Exact index arithmetic differs slightly (old `filterItemAdapter.move(position, firstUnpinned)`;
  new remove/insert adjusted by -1). The visible result is the same group placement.
- E2E: yes. Pin tag `A`, restart, assert that `A` is 2nd, and assert that Settings lists `A`.

**LIST-12 — Share a tag as JSON**
- When "Share as json" is chosen, Then a centred progress spinner shows, then the system chooser titled "Share
  file through" opens with an `application/json` file of that tag's logs. The spinner hides.
- Old: `P/present/LogActivity.kt:227-250`
- New: `P/present/LogActivity.kt:290-301`; `P/present/LogScreen.kt:153-155`
- Status: SAME
- E2E: yes. Assert that the chooser (system UI) appears.

**LIST-13 — Remove a custom filter**
- When "Remove" is chosen, Then the chip disappears and the filter is deleted from storage. If it was selected,
  "All" becomes selected and reloads.
- Old: `P/present/LogActivity.kt:209-225`
- New: `P/present/LogActivity.kt:276-288`
- Status: SAME
- E2E: yes.

**LIST-14 — Create custom filter dialog**
- When "Create custom filter" is chosen, Then a dialog shows three fields ("title", "contain tag", "search
  value") and a "confirm" button.
- Old: `R/layout/dialog_filter.xml:9-52`; `P/present/LogActivity.kt:539-547`
- New: `P/present/FilterDialog.kt:28-82`; `P/present/FilterDialogHost.kt:12-43`; `P/present/LogActivity.kt:310-312`
- Status: SAME
- E2E: yes.

**LIST-15 — Validation when title and tag are both empty**
- Old: both fields show the inline error text "tag or title required", and the dialog stays open.
  Whitespace-only input counted as filled.
- New: both fields turn red (error colour) with **no message**. Whitespace-only input is rejected.
- Old: `P/present/FilterDialog.kt:36-43`
- New: `P/present/FilterDialog.kt:62-66`
- Status: CHANGED-INTENTIONALLY (`2026-08-28-filter-dialog-compose-migration-design.md`, "Behavior parity" and
  "Not carried over")
- E2E: yes. Old: assert the text. New: assert that the dialog stays open.

**LIST-16 — Confirm a filter**
- When confirmed with a title and/or tag, Then the dialog closes and a new chip is appended (title, or the tag if
  the title is empty). It becomes selected and its logs load. The filter persists.
- Old: `P/present/FilterDialog.kt:44-53`; `P/present/LogActivity.kt:309-313`
- New: `P/present/FilterDialog.kt:67-73`; `P/present/LogActivity.kt:204-206`
- Status: SAME
- E2E: yes. Title "Errs", tag empty, search "boom": assert that the chip "Errs" is selected and only values
  containing "boom" are listed.

**LIST-17 — New filter id clashes with an existing chip id**
- Given a filter is created whose title equals an existing chip's id (for example "okhttp", "Index", an existing
  tag, or an existing custom title):
  - Old: the chip list simply showed a duplicate chip.
  - New: the duplicate chip is shown too. The chip `LazyRow` key is `"$index-${type}-${id}"`, so duplicate ids
    no longer give duplicate Compose keys (before the fix, `key = { it.id }` was expected to crash).
- The same applies when a notification target tag equals a custom filter's id (`withNotificationTarget` only
  de-dupes against `TagFilterItem`s): the appended tag chip is shown and selected, and its logs are loaded.
- Selection is still by id, as in the old UI (`getIndexOfFilter(id)` there, `optCurrentFilterItem()` here). So
  with duplicate ids, the first matching chip decides later refresh, next-page and search loads. Visually, the
  old UI highlighted only the first match; the new UI highlights every chip with that id. This is unchanged
  data semantics, not part of the fix.
- Old: `P/present/LogActivity.kt:282`; `P/present/ReaderViewModel.kt:224-248`
- New: `P/present/FilterChipsRow.kt:55-58`; `P/present/LogActivity.kt:214-218`
- Status: SAME (fixed; the crash was a regression)
- E2E: yes. Log tag `A`, create a filter titled "A", and assert that the app is still alive.

**LIST-18 — Dismiss the filter dialog**
- When back is pressed or the outside is tapped, Then the dialog closes and no filter is added.
- Old: `DialogFragment` default
- New: `P/present/FilterDialog.kt:35`
- Status: SAME
- E2E: yes.

**LIST-19 — Clear custom filters**
- When "Clear custom filters" is chosen, Then every custom chip disappears (storage is cleared for every client),
  "All" is selected, and the logs reload. No confirmation, no toast.
- Old: `P/present/LogActivity.kt:426-428`; `P/present/ReaderViewModel.kt:168-191`
- New: `P/present/LogActivity.kt:151`; `P/present/ReaderViewModel.kt:212`
- Status: SAME
- E2E: yes.

**LIST-20 — Search row visibility and hint**
- Given a **tag** chip is selected, Then the first list row is a search field (it scrolls with the list).
  - Hint for the okhttp tag: "Contain text in url".
  - Hint for other tags: "Contain text in log value".
- With "All" or a custom filter selected, there is no search row.
- Old: `P/present/LogActivity.kt:390-419`; `P/present/ReaderViewModel.kt:439-450`
- New: `P/present/LogActivity.kt:236-237,163-166`; `P/present/ReaderViewModel.kt:483`
- Status: SAME
- E2E: yes.

**LIST-21 — Search behaviour**
- When text is typed, Then about 700 ms after typing stops the list reloads with:
  - Text tags: value **contains** the text.
  - okhttp: url meta **contains** the text.
- Clearing the text restores the full tag list. Tapping any chip, including the same one, empties the field.
- Old: `P/present/LogActivity.kt:263-273`; `P/present/itemview/SearchItemView.kt:23-40`
- New: `P/present/LogActivity.kt:177-188`; `P/present/logitem/SearchBarRow.kt:35-49`
- Status: SAME
- E2E: yes. Values "alpha" and "beta" under tag A: type "alp", wait more than 0.7 s, and assert that only
  "alpha" is listed.

**LIST-22 — Search widget appearance**
- Old: a platform `SearchView`, iconified by default. The user taps the magnifier to expand it, and it has a
  built-in clear (×) button.
- New: an always-expanded `OutlinedTextField` with a leading search icon and the hint as placeholder. It has a
  trailing clear (×) button (content description "Clear search") while the text is not empty. Tapping it empties
  the field and reports the empty query upward, the same way deleting the text does (debounced search, then the
  full tag list).
- Old: `R/layout/item_search.xml`
- New: `P/present/logitem/SearchBarRow.kt:41-65` (clear button `:49-60`)
- Status: CHANGED-INTENTIONALLY for the expanded field (`2026-08-30` spec decision 4: TextField replaces
  SearchView). The clear (×) button is SAME (fixed; it was missing).
- E2E: yes. Old: tap the magnifier first. New: type directly.

**LIST-23 — Search plus pagination**
- Old: `SearchItemView.searchText` was never updated from typing, so pages 2+ loaded **without** the search
  text (unfiltered rows were mixed into a search result).
- New: `searchRow.text` is updated after the debounce, so later pages keep the filter.
- Old: `P/present/LogActivity.kt:349-353`
- New: `P/present/LogActivity.kt:182,226-230`
- Status: CHANGED-UNDOCUMENTED (bug fix)
- E2E: yes. Insert 60 logs with tag A (30 contain "x"), search "x", scroll to the end, and assert that every row
  contains "x".

**LIST-24 — Pagination**
- When the list is scrolled near the end, Then the next 40 logs are appended. This continues until a page comes
  back with fewer than 40.
- Old: it triggers only while scrolling down, 5 rows before the end.
- New: it triggers whenever the last visible row is within 3 of the end, even without scrolling (on a tall
  screen a short page can auto-load the next).
- Old: `P/uikit/component/EndlessScrollListener.kt:8-25`; `P/present/LogActivity.kt:137-142,253-261`
- New: `P/present/LogScreen.kt:78-88`; `P/present/LogActivity.kt:168-175,344-366`
- Status: SAME (trigger detail: CHANGED-UNDOCUMENTED, minor)
- E2E: yes. Insert 100 logs, scroll to the bottom, and assert that the oldest value is visible.

**LIST-25 — Refresh**
- When Refresh is tapped, Then the logs and chips are reloaded for the current filter. Then a short toast shows:
  "refreshed" if the list isn't empty, otherwise "Empty filter".
- Old: `P/present/LogActivity.kt:299-307,527-532`
- New: `P/present/LogActivity.kt:198-202,303-308`
- Status: SAME
- E2E: yes. Insert a log while on the screen, refresh, and assert that it appears. The toast needs a toast
  watcher.

**LIST-26 — Clear all logs**
- When the clear icon is tapped, Then every log of this client is deleted **without confirmation**. The list
  becomes empty, the chips become "All" plus the custom filters, and "All" is selected. No toast.
- Old: `P/present/LogActivity.kt:422-424`; `P/present/ReaderViewModel.kt:146-166`
- New: `P/present/LogScreen.kt:95`; `P/present/ReaderViewModel.kt:190`
- Status: SAME
- E2E: yes. Then query the provider and assert 0 rows for the client.

**LIST-27 — Empty list**
- Given the client has no logs (or the filter matches none), Then the list area is blank. There is no
  empty-state message.
- Old/New: no empty view in `activity_log.xml` / `LogScreen.kt`
- Status: SAME
- E2E: yes.

**LIST-28 — Toggle theme**
- When "Toggle theme" is chosen, Then the app switches between light and dark (all activities recreate). The
  choice persists across launches. After the recreate the list reloads with "All" selected (LIST-30).
- Old: `P/present/ReaderViewModel.kt:468-476`; `P/arch/BaseActivity.kt:22-27`
- New: `P/present/ReaderViewModel.kt:512`; `P/arch/BaseActivity.kt` (unchanged); `P/uikit/theme/Theme.kt:24-26`
- Status: SAME
- E2E: yes (screenshot/background colour, then relaunch).

**LIST-29 — Open Settings / Export**
- "Settings" opens the Settings screen (SET-*). "Share/Save logs" opens the export dialog (EXP-*). In the new UI
  a double tap opens the dialog only once.
- Old: `P/present/LogActivity.kt:503-521,430-434`
- New: `P/present/LogActivity.kt:153-159,117-124`
- Status: SAME
- E2E: yes.

**LIST-30 — Rotation / recreate**
- When the screen is rotated, Then the logs are fetched again from the start and "All" is selected. Any
  notification target tag in the launch intent is applied again.
- Old: `P/present/LogActivity.kt:101-156`
- New: `P/present/LogActivity.kt:89-109`
- Status: SAME
- E2E: yes.

**LIST-31 — Back**
- When back is pressed on the list, Then the screen closes and Starter is shown (START-07).
- Status: SAME
- E2E: yes.

**LIST-32 — Return from a detail page**
- When the user comes back from the log detail page, Then the scroll position, the selected chip and the search
  text are kept.
- Old: separate Activity, so the list Activity was untouched.
- New: navigation destination; `LazyListState` is saveable.
- Old: `P/present/LogActivity.kt:549-551`
- New: `P/present/LogActivity.kt:141-146`; `P/present/LogScreen.kt:74`
- Status: SAME. Spec 2026-10-15 notes an edge: characters typed in the last 700 ms before tapping may be lost.
- E2E: yes.

## 5. Log list rows

**ROW-01 — Date header**
- Given logs from different days, Then each day starts with a centred pill showing the date (Persian calendar,
  `yyyy-M-d`). Date headers carry over across pages: one header per day.
- Old: `P/log/HeaderLogDateItem.kt:21-24`; `P/present/ReaderViewModel.kt:90-107`
- New: `P/present/logitem/DateHeaderRow.kt:19-37`
- Status: SAME
- E2E: yes. Insert logs on two days and assert two header texts.

**ROW-02 — Text row layout**
- Then each text log row shows:
  - A divider.
  - A 40 dp circular level icon.
  - An uppercase level badge: ERROR, WARN, INFO or DEBUG.
  - The time.
  - A tag chip (see ROW-05).
  - The message.
- Error uses the info icon, Warn the warning icon, Info the info icon, Debug/Verbose the bug icon. Each level has
  its own colour.
- Old: `R/layout/item_log.xml:10-95`; `P/log/TextLogItem.kt:28-131`
- New: `P/present/logitem/TextLogRow.kt:39-129`
- Status: SAME (colours: GEN-02)
- E2E: yes. Use `_meta {"log_level":6}` and assert the badge text "ERROR".

**ROW-03 — Verbose label**
- Given `log_level` = 2, Then the badge read "INFO" (old string `log_item_verbose` = "Info") and now reads
  "VERBOSE".
- Old: `R/values/strings.xml:93`
- New: `R/values/strings.xml:98`; `P/present/logitem/TextLogRow.kt:47`
- Status: CHANGED-INTENTIONALLY (commit `72bd92e`: "Verbose logs are labelled 'Verbose' instead of 'Info'")
- E2E: yes.

**ROW-04 — Row time has milliseconds**
- Given a log with `_date` = …`123` ms, Then the row time reads `HH:mm:ss.123` (4.5.0 CHANGELOG: "Show
  millisecond precision on log list item time").
- Old: `P/log/TextLogItem.kt:30` (`calender.getFormattedWithMillis(data.timePresent)`)
- New: `P/present/logitem/TextLogRow.kt:60` (`calendar.getFormattedWithMillis(data.timePresent)`).
- The other rows were checked against their old item views and already matched: HTTP (`getTimeText`, `HH:mm:ss`),
  image (`getFormatted()` date and time), session start (`getFormatted()` date and time) and the date header.
  None of them had milliseconds in the old UI.
- Status: SAME (fixed; `TextLogRow` had lost the milliseconds)
- E2E: yes. Use `_date:l:1760950000123` and assert that the row text matches `\d\d:\d\d:\d\d\.123`.

**ROW-05 — Tag chip visibility on the "All" filter**
- Old: rows were built with `ignoreShowingTag = isIndexFilterSelected()`, so the tag chip was **hidden** when
  "All" was the selected chip at bind time. In practice:
  - First load: tags shown (nothing selected yet).
  - After a refresh or next page on "All": hidden.
  - On a chip switch the check used the *previous* selection, so the result depended on order.
- New (user decision): the tag chip is shown when "All" or a custom filter is selected. It is hidden only when a
  tag chip (`TagFilterItem`) is selected, because every row then has that tag. The rule is worked out from the
  current selection on each recomposition, so it doesn't depend on chip order. The import viewer has no filters
  and always shows the chip.
- Old: `P/present/LogActivity.kt:257,287-292`; `P/log/TextLogItem.kt:36-41`
- New: `P/present/LogActivity.kt:136` (`ignoreTagChip = optCurrentFilterItem() is TagFilterItem`);
  `P/present/LogScreen.kt:190`; `P/present/ImportLogScreen.kt:70` (`ignoreTagChip = false`);
  `P/present/logitem/TextLogRow.kt:108-118`
- Status: CHANGED-INTENTIONALLY (user decision 2026-10-20: hide the tag chip only on a tag filter; the old rule
  was inconsistent and hid it on "All")
- E2E: yes. On "All", tap Refresh: tags are visible. Select a tag chip: tags are hidden. Select a custom filter:
  tags are visible.

**ROW-06 — Long message preview**
- Old: the full message was always rendered.
- New: the row shows at most 10 lines and 2,000 characters, ending in "…". The full text is on the detail page.
- Old: `P/log/TextLogItem.kt:35`
- New: `P/present/logitem/RowTextPreview.kt:6-15`; `P/present/logitem/TextLogRow.kt:61,119-126`
- Status: CHANGED-INTENTIONALLY (commit `2848a5e`)
- E2E: yes. Insert a 5,000-character value and assert that the row ends in "…" while detail shows the full text.

**ROW-07 — Unknown log level (for example 7 = ASSERT)**
- Old: no style applied, so the badge kept the XML placeholder "INFO" (unstyled) or a recycled row's style.
- New: no badge and an empty icon circle.
- Old: `P/log/TextLogItem.kt:44-92`; `R/layout/item_log.xml:59`
- New: `P/present/logitem/TextLogRow.kt:48,80-100`
- Status: CHANGED-UNDOCUMENTED (edge)
- E2E: yes (`{"log_level":7}`).

**ROW-08 — Tap a text row**
- When a text row is tapped, Then the log detail page opens (DET-*).
- Old: `P/present/LogActivity.kt:123-134,549-551`
- New: `P/present/LogActivity.kt:141-146`
- Status: SAME
- E2E: yes.

**ROW-09 — HTTP row**
- Then the HTTP row shows the status in a left column:
  - The code, red if 400–600.
  - "Requested" while pending.
  - "!!!" (red) on network failure.
  - Empty if unsupported.
- To its right:
  - "METHOD /path" in the status colour. Old max 2 lines, new max 3 lines, ellipsised.
  - "scheme://domain" in a secondary colour. Old unlimited, new max 3 lines.
  - The time `HH:mm:ss` (no millis in either version).
- Old: `P/log/HttpLogItemView.kt:26-63`; `R/layout/item_http.xml:36-37`
- New: `P/present/logitem/HttpLogRow.kt:24-81`
- Status: SAME. The path line limit of 2 → 3 and the domain cap are CHANGED-INTENTIONALLY (commit `2848a5e`).
- E2E: yes (sample-app OkHttp calls).

**ROW-10 — Tap an HTTP row**
- When an HTTP row is tapped, Then the HTTP detail screen opens (HTTP-*).
- Old: `P/present/LogActivity.kt:355-357`
- New: `P/present/LogActivity.kt:147`
- Status: SAME
- E2E: yes.

**ROW-11 — Image row info**
- Then the image row shows bold-prefixed lines:
  - "Status: " followed by "Succeed" (green), "Failed" (red), or nothing.
  - "Name: ".
  - "Date: ".
  - "Time: ".
  - After the image loads: "Image size: WxH" and "File size: N kB".
- Then a "URL: …" line, then the image if it loaded.
- While loading, the old row showed only "**Loading**". The new row shows "**Status: Loading**".
- For a failed image (status 2) the old row never started a load, so it stayed on "Loading" forever. The new row
  shows the full info with "Failed".
- Old: `P/log/ImageLogItem.kt:37-119`
- New: `P/present/logitem/ImageLogRow.kt:51-146`
- Status: SAME for the content. CHANGED-UNDOCUMENTED for the loading/failed text (failed case: bug fix).
- E2E: yes. Use `_typeId dvn_image`, `{"url":"https://…/x.png","status":2}`, and assert the "Failed" text.

**ROW-12 — Copy an image URL**
- When the "URL: …" line is tapped, Then the toast "URL Copied" (short) shows and the clipboard holds the full
  URL. The rest of the image row is not tappable.
- Old: `P/log/ImageLogItem.kt:42-49`
- New: `P/present/logitem/ImageLogRow.kt:129-140`
- Status: SAME
- E2E: yes. Read the clipboard via a test helper or `cmd clipboard` (API 31+).

**ROW-13 — Session start row**
- Given a log with tag `SessionStart`, Then a highlighted full-width row shows:
  - A dot.
  - Bold "Session started".
  - A pill "v<version>" (only if a version was parsed from the payload or the message).
  - A pill with the date.
  - The time on the right.
- It is not tappable.
- Old: `P/log/SessionStartLogItem.kt:23-37`; `R/layout/item_session_start.xml`
- New: `P/present/logitem/SessionStartRow.kt:28-76`
- Status: SAME (pill colours: GEN-02)
- E2E: yes. Use `_tag SessionStart` and `_meta {"payload":"App Version: 1.2.3 (45)"}`, then assert "v1.2.3".

**ROW-14 — Non-interactive rows**
- Then date headers, session rows and image rows (apart from the URL line) have no tap or long-press action.
- Status: SAME
- E2E: yes.

## 6. Log detail page

**DET-01 — Content**
- When a text log is opened, Then a full page titled "Log detail" shows these label/value rows:
  - "Tag".
  - "Time": local `HH:mm:ss.SSS`.
  - "Message": full text.
  - "Meta": the raw `_meta` JSON string, only when the log has meta.
- Old: `P/present/LogItemDetailActivity.kt:43-63`; `R/layout/activity_log_item_detail.xml`
- New: `P/present/LogItemDetailScreen.kt:39-96`; `P/present/LogDetailDestination.kt:20-36`
- Status: SAME
- E2E: yes. Assert the tag, `…\.123`, the message, and the meta JSON. A log without meta shows no "Meta" label.

**DET-02 — Copy**
- When the copy icon (content description "Copy log detail") is tapped, Then the clipboard holds the lines
  `Tag: …`, `Time: …`, `Message: …` and, if present, `Meta: …`, and the toast "Copied" (short) shows. If the
  clipboard throws, the toast "Failed to copy, User other available options" shows instead.
- Old: `P/present/LogItemDetailActivity.kt:79-89`; `P/AppExt.kt:135-143`
- New: `P/present/LogItemDetailCopy.kt:9-19`
- Status: SAME
- E2E: yes (clipboard).

**DET-03 — Back arrow**
- Old: no navigation icon; only system back.
- New: a back arrow ("Navigate up", mirrored in RTL) returns to the list.
- New: `P/present/LogItemDetailScreen.kt:51-61`
- Status: CHANGED-INTENTIONALLY (`2026-10-15-log-item-detail-compose-migration-design.md`, decision 4)
- E2E: yes.

**DET-04 — System back**
- When system back is pressed, Then the list is shown again with its state kept (LIST-32).
- Status: SAME
- E2E: yes.

**DET-05 — Selectable text**
- Old: only Message and Meta were selectable.
- New: all values, including Tag and Time, are selectable. Labels are not.
- Old: `R/layout/activity_log_item_detail.xml:91,112`
- New: `P/present/LogItemDetailScreen.kt:73-133`
- Status: CHANGED-UNDOCUMENTED (minor; the spec claims "as in the XML")
- E2E: no.

**DET-06 — Double tap on a row**
- Old: a fast double tap could stack two detail Activities.
- New: only one page opens.
- New: `P/present/LogActivity.kt:142-145`; `P/present/LogDetailDestination.kt:38-40`
- Status: CHANGED-INTENTIONALLY (commit `4a74783`)
- E2E: yes. Double tap, then back once, and assert that the list is shown.

**DET-07 — Large and special-character messages**
- Given a message with `& ? / % #`, newlines, emoji, the literal text "null", or about 50k characters, Then it
  shows exactly as logged and nothing crashes. Very large values are drawn in chunks (a manual selection that
  crosses a chunk boundary may drop one newline; Copy is exact).
- New: `P/present/LogDetailArgs.kt:39-52`; `P/uikit/text/ChunkText.kt`
- Status: SAME (the visible result). The implementation hardening is CHANGED-INTENTIONALLY (spec 2026-10-15,
  "Changes from review").
- E2E: yes.

**DET-08 — Rotation / theme toggle on detail**
- Then the page stays on screen with the same content.
- Old: Intent extras
- New: back-stack arguments
- Status: SAME
- E2E: yes.

## 7. Export dialog ("Share/Save logs")

**EXP-01 — Mode selection**
- When the dialog opens, Then two radio options show: "Default" (selected) and "Builder". With Default, the
  explanation reads "It export all logs in a zip file that contain: - index file that has all logs. - logs for
  each tag."
- Old: `R/layout/dialog_log_export.xml:14-30,80-81`; `P/present/LogExportDialog.kt:71-80`
- New: `P/present/LogExportDialog.kt:121-167`
- Status: SAME
- E2E: yes.

**EXP-02 — Builder fields**
- When "Builder" is chosen, Then the dialog shows:
  - The field "Whitelist tag filter,(example: a,b,c), Default is all".
  - The field "Day filter, default is no filter", pre-filled with "1", numeric.
  - The checkbox "Separate tag files", unchecked.
- Old: `R/layout/dialog_log_export.xml:38-74`
- New: `P/present/LogExportDialog.kt:129-161`
- Status: SAME
- E2E: yes.

**EXP-03 — Layout height when switching modes**
- Old: the hidden part was `invisible`, so the dialog height stayed stable.
- New: the content swaps, so the height can change.
- Status: CHANGED-INTENTIONALLY (`2026-10-16-log-export-dialog-compose-migration-design.md`, "acceptable to
  let it change")
- E2E: no.

**EXP-04 — Save Logs**
- When "Save Logs" is tapped, Then a progress spinner shows. Then the system create-document picker opens (in a
  chooser titled "Share/Save logs"), proposing `Devin_<yyyy-M-dTHH:mm:ss>.zip`. It proposes `.json` instead in
  Builder mode with "Separate tag files" unchecked.
- After a location is picked, the file is written, the toast "Save done" (long) shows and the dialog closes. If
  writing fails, the toast is "Something went wrong!".
- Old: `P/present/LogExportDialog.kt:82-98,118-141`; `P/present/ExportViewModel.kt` (`createIntentForSave`)
- New: `P/present/LogExportDialog.kt:63-82`; `P/present/ExportViewModel.kt:85-117,184`
- Status: SAME
- E2E: yes (system DocumentsUI). Check the file in the chosen folder.

**EXP-05 — Save picker cancelled**
- Then the dialog stays open, with no toast and no spinner.
- Old: `P/present/LogExportDialog.kt:123`
- New: `P/present/ExportViewModel.kt:97-103`
- Status: SAME
- E2E: yes.

**EXP-06 — Share Logs**
- When "Share Logs" is tapped, Then a spinner shows, the chooser "Share file through" opens with the export file,
  and the dialog closes.
- Old: always MIME `application/json`, even for a zip.
- New: `application/zip` for a zip export, `application/json` otherwise.
- Old: `P/present/LogExportDialog.kt:100-114`
- New: `P/present/ExportViewModel.kt:119-129`
- Status: CHANGED-INTENTIONALLY (MIME, spec 2026-10-16 export "Bugs fixed"). The rest is SAME.
- E2E: yes. Assert the chooser; capture the intent type with a test receiver app.

**EXP-07 — Export file content**
- Default mode: a zip with `index.json` (all logs) plus one `<tag>.json` per tag.
- Builder:
  - With a whitelist "a,b", only those tags are exported, and there is no index.
  - With separate unchecked, a single JSON file.
- Old/New: `P/present/ExportViewModel.kt` `Common.prepareLogsForExport` (unchanged; new lines 236-300)
- Status: SAME
- E2E: yes (save to a folder, then pull and unzip).

**EXP-08 — Day filter semantics**
- The days value is converted with `inWholeMicroseconds` on a millisecond clock, and the DAO selects
  `date < bound`, so the filter does not mean "last N days". Kept unchanged on purpose.
- Old/New: `ExportViewModel.Common.getUpDaysConstraintAsCurrentMills`
- Status: SAME (a known bug, documented in spec 2026-10-16 export "Not fixed here")
- E2E: no (don't encode the bug in tests).

**EXP-09 — Days field input**
- Old: `inputType="number"` (no minus sign on the soft keyboard).
- New: non-digits are filtered out and negative values are ignored.
- Old: `R/layout/dialog_log_export.xml:64`
- New: `P/present/LogExportDialog.kt:88,97`
- Status: CHANGED-INTENTIONALLY (spec 2026-10-16 export: "-1 used to crash")
- E2E: yes (`adb shell input text -- -1`, then assert the field shows "1").

**EXP-10 — Buttons while exporting**
- Old: the buttons stayed enabled; tapping again cancelled and restarted the export.
- New: both buttons are disabled while exporting. The spinner also stays on while the save picker is open.
- New: `P/present/LogExportDialog.kt:169-179`; `P/present/ExportViewModel.kt:89-93`
- Status: CHANGED-INTENTIONALLY (spec 2026-10-16 export, "buttons disabled")
- E2E: no.

**EXP-11 — Rotation while the save picker is open**
- Old: the save was silently dropped (the temp-file reference was lost).
- New: the file is saved and the toast "Save done" shows.
- New: `P/present/ExportViewModel.kt:64-151`; `P/present/LogActivity.kt:117-124`
- Status: CHANGED-INTENTIONALLY (commit `a288209`)
- E2E: yes.

**EXP-12 — Error while preparing**
- New: the toast "Something went wrong!" shows and the dialog stays open.
- Old: the exception was unhandled (the spinner stuck, or a crash).
- New: `P/present/ExportViewModel.kt:131-145`
- Status: CHANGED-INTENTIONALLY (spec 2026-10-16 export)
- E2E: no.

**EXP-13 — Dismiss**
- When back is pressed or the outside is tapped, Then the dialog closes. Any export in progress is cancelled and
  no toast follows.
- Status: SAME
- E2E: yes.

**EXP-14 — Typed values survive rotation**
- Then the mode, both text fields and the checkbox are kept.
- Old: view state was restored by the `DialogFragment`.
- New: `rememberSaveable` (`P/present/LogExportDialog.kt:55-58`).
- Status: SAME
- E2E: yes.

## 8. Settings

**SET-01 — Screen and back**
- Then the screen is titled "Settings" with a back arrow that closes it. System back closes it too.
- Old: `R/layout/activity_settings.xml:8-16`; `P/present/settings/SettingsActivity.kt:46`
- New: `P/present/settings/SettingsScreen.kt:90-106`
- Status: SAME
- E2E: yes.

**SET-02 — Auto-delete switch**
- Then the Storage section shows the header "Storage" and the switch "Auto-delete old logs" (on by default),
  with the summary "Keeps storage small by removing old logs automatically". Toggling it persists.
- Old: `P/present/settings/SettingsActivity.kt:48-52`
- New: `P/present/settings/SettingsScreen.kt:114-120,199-216`; `P/present/settings/SettingsActivity.kt:81-84`
- Status: SAME
- E2E: yes. Toggle, reopen Settings, and assert the state.

**SET-03 — Keep logs for**
- Then a row shows "Keep logs for" with the value "7 days" by default. Tapping it opens a dialog titled "Keep
  logs for" with radio options "1 day", "3 days", "7 days", "14 days", "30 days". Tapping an option saves it and
  closes the dialog. There are no buttons; back or an outside tap cancels.
- Old: `P/present/settings/SettingsActivity.kt:78-110`
- New: `P/present/settings/SettingsScreen.kt:121-126,148-153,292-325`
- Status: SAME
- E2E: yes.

**SET-04 — Max logs per app**
- Then a row shows "Max logs per app" with the value "10,000" by default. Its options are "1,000", "5,000",
  "10,000", "50,000" and behave as in SET-03.
- Old: `P/present/settings/SettingsActivity.kt:90-100`
- New: `P/present/settings/SettingsScreen.kt:127-132,154-159`
- Status: SAME
- E2E: yes.

**SET-05 — Rows disabled when the switch is off**
- Given auto-delete is off, Then both value rows are drawn at 38% alpha and don't respond to taps.
- Old: `P/present/settings/SettingsActivity.kt:71-76,172-175`
- New: `P/present/settings/SettingsScreen.kt:219-230`
- Status: SAME
- E2E: yes.

**SET-06 — Storage used**
- Then a row shows "Storage used": "Calculating…" first, then a short size (for example "1.2 MB"), or "—" if the
  calculation fails.
- Old: `P/present/settings/SettingsActivity.kt:112-122`
- New: `P/present/settings/SettingsActivity.kt:121-130`; `P/present/settings/SettingsScreen.kt:233-245`
- Status: SAME
- E2E: yes. Assert the value matches `\d.*B`.

**SET-07 — Pinned filter tags**
- Then the section "Pinned filter tags" shows the summary "Pinned tags show first in the filter bar of every app
  that logs them", one chip per pinned tag (sorted; `okhttp` is pinned by default on first use), and an "Add
  tag" chip.
- Each tag chip has a close icon (content description "Unpin <tag>"). Tapping it unpins the tag and removes the
  chip.
- Old: `P/present/settings/SettingsActivity.kt:124-144`; `P/data/FilterRepository.kt:127-150`
- New: `P/present/settings/SettingsScreen.kt:136-142,249-289`
- Status: SAME
- E2E: yes. Fresh install: assert the chip "okhttp", unpin it, then assert the log list's "http" chip has no pin
  icon after a refresh.

**SET-08 — Add a pinned tag**
- When "Add tag" is tapped, Then a dialog "Add tag" opens with a single-line field (hint "Tag, e.g. okhttp") and
  "OK"/"Cancel". On OK, the trimmed text is pinned (blank is ignored) and a chip appears. The tag may be one no
  client has logged yet.
- Old: `P/present/settings/SettingsActivity.kt:146-165`
- New: `P/present/settings/SettingsScreen.kt:160-163,328-352`
- Status: SAME
- E2E: yes.

**SET-09 — IME Done in the add-tag dialog**
- New: pressing Done on the keyboard confirms, like OK. Old: no IME action.
- New: `P/present/settings/SettingsScreen.kt:339-340`
- Status: NEW-ONLY
- E2E: yes.

**SET-10 — Rotation with a dialog open**
- Old: the picker or add dialog was lost on rotation.
- New: the open dialog and the typed text survive.
- New: `P/present/settings/SettingsScreen.kt:87,329`
- Status: CHANGED-INTENTIONALLY (commit `ee484d6`)
- E2E: yes.

**SET-11 — Retention effect**
- Given auto-delete is on, When logs are inserted, Then logs older than N days or beyond the newest N per client
  are deleted in the background. It runs on the first insert after process start, then at most every 10
  minutes; a settings change resets the throttle.
- Old/New: `P/retention/RetentionObserver.kt:23-75`; `RetentionCleaner.kt` (identical)
- Status: SAME
- E2E: yes. Set "1 day", insert a log dated 3 days ago plus a new one, and assert the old one is gone from the
  list after a refresh.

## 9. Import viewer (ImportLogActivity)

**IMP-01 — Reachability**
- The screen can't be opened from the UI in either version: the "Import logs as json" menu item is commented
  out, and the Activity is `exported=false`.
- Old: `R/menu/main_menu.xml:17-21`; `P/present/LogActivity.kt:498-501,534-537`
- New: `P/present/LogActivity.kt:94-96,334-340` (launcher kept, no entry point)
- Status: SAME
- E2E: no (unreachable for a black-box test). IMP-02..06 apply only if it is re-enabled.

**IMP-02 — Search filter**
- Then a "Contain value" field sits above the list. Text logs are kept when they contain the query (case
  insensitive). Every other row is always kept. Tag chips are shown on rows.
- Old: `P/present/ImportLogActivity.kt:93-112`
- New: `P/present/ImportLogScreen.kt:44-89`
- Status: SAME
- E2E: no.

**IMP-03 — Taps**
- A text row opens the log detail page (same UI as DET-*). An HTTP row shows the toast "Coming soon".
- Old: `P/present/ImportLogActivity.kt:76-91,131-133`
- New: `P/present/ImportLogActivity.kt:62-79`
- Status: SAME
- E2E: no.

**IMP-04 — Read errors**
- Unreadable or missing URI: toast "Something went wrong!" (long), then the screen closes.
- Empty file: toast "Empty file text!", then the screen closes.
- Old: `P/present/ImportLogActivity.kt:65-72`
- New: `P/present/ImportLogActivity.kt:82-99`
- Status: SAME
- E2E: no.

**IMP-05 — Non-empty file with no logs (or malformed JSON)**
- Old: an empty list was shown.
- New: the toast "Empty file text!" shows and the screen closes. Also new: a progress spinner while reading, and
  rotation doesn't re-read the file.
- New: `P/present/ReaderViewModel.kt:94-119`; `P/present/ImportLogScreen.kt:62-66`
- Status: CHANGED-INTENTIONALLY (commit `74d56f4`)
- E2E: no.

## 10. HTTP log detail

**HTTP-01 — Layout and tabs**
- Then there is no title bar. A tab row shows "Overview", "Request", "Response", with an overflow (⋮) icon on
  the right. Tapping a tab or swiping changes pages. Back closes the screen.
- Old: `R/layout/activity_http_log_detail.xml`; `P/present/http/HttpLogDetailActivity.kt:96-114`
- New: `P/present/http/HttpLogDetailScreen.kt:67-141`
- Status: SAME
- E2E: yes.

**HTTP-02 — Loading and failure**
- A spinner shows while loading.
- Old: if the log couldn't be loaded, the tabs stayed empty forever.
- New: the text "Something went wrong!" shows.
- Old: `P/present/http/HttpLogDetailActivity.kt:87-94`
- New: `P/present/http/HttpLogDetailScreen.kt:77-86`; `P/present/http/HttpDetailViewModel.kt` (`isLoadFailed`)
- Status: CHANGED-INTENTIONALLY (commit `f81852a`)
- E2E: partial. Delete the log row while the list is open, then tap the row.

**HTTP-03 — Overview fields**
- Then the Overview tab shows bold labels with values, in this order:
  1. URL
  2. Method
  3. Protocol
  4. Status
  5. Response
  6. SSL (Yes/No)
  7. TLS version (only if known)
  8. Cipher Suite (only if known)
  9. Request time
  10. Response time
  11. Duration
  12. Request size
  13. Response size
  14. Total size
- Old: `P/present/http/items/HttpDetailOverviewItemView.kt:24-52`; `R/layout/item_http_detail_overview.xml`
- New: `P/present/http/HttpDetailOverviewPage.kt:28-61`
- Status: SAME
- E2E: yes. Use sample-app requests and assert the labels plus the URL value.

**HTTP-04 — Overview values selectable**
- Old: not selectable. New: values are selectable (labels are not).
- New: `P/present/http/HttpDetailOverviewPage.kt:32,66-73`
- Status: CHANGED-UNDOCUMENTED (minor; the spec said "if the XML values were selectable", and they weren't)
- E2E: no.

**HTTP-05 — Headers**
- Then the Request and Response tabs start with the headers, one per line as **"Name: "** value, or the text
  "(header is empty)". They are selectable.
- Old: `P/present/http/items/HttpDetailContentItemView.kt:124-134`; `P/present/http/HttpFormatUtils.kt:37-49`
- New: `P/present/http/HttpBodyContent.kt:62-76`; `P/present/http/HttpDetailContentPage.kt:226-239`
- Status: SAME
- E2E: yes.

**HTTP-06 — Empty body**
- Given no body, Then "(body is empty)" shows under the headers, with no search field and no copy row.
- Old: `P/present/http/items/HttpDetailContentItemView.kt:135-138`
- New: `P/present/http/HttpDetailContentPage.kt:95,115-117,240-246`
- Status: SAME
- E2E: yes (a GET request tab).

**HTTP-07 — Body rendering**
- Then the body is pretty-printed and JSON-coloured, in the Fira Code font, left-to-right, one row per line, and
  selectable. Very long lines are split into display chunks so nothing crashes.
- Old: `P/present/http/items/HttpDetailContentItemView.kt:176-186`; `R/layout/item_http_body_row.xml`
- New: `P/present/http/HttpBodyContent.kt:78-104`; `P/present/http/HttpDetailContentPage.kt:255-280`
- Status: SAME (colour values: GEN-02; chunking: CHANGED-INTENTIONALLY, `f81852a`)
- E2E: yes. Assert a JSON key from the response is visible.

**HTTP-08 — Copy row**
- Then a row above the body offers "Copy body" (copies the formatted body text) and "Copy cURL". Each shows the
  toast "Copied".
- Old: `P/present/http/items/HttpCopyActionsItemView.kt:23-34`
- New: `P/present/http/HttpDetailContentPage.kt:248-254,283-310`; `P/present/http/HttpLogDetailActivity.kt:187-191`
- Status: SAME
- E2E: yes (clipboard).

**HTTP-09 — Search field**
- Old: a `SearchView` (hint "Search in body"), iconified by default, so the user taps the magnifier first.
- New: an always-expanded field with the placeholder "Search in body", a search icon, a clear (×) button when
  not empty, and IME "Search" hiding the keyboard.
- Old: `R/layout/item_http_detail_content.xml:21-28`
- New: `P/present/http/HttpDetailContentPage.kt:139-157`
- Status: CHANGED-INTENTIONALLY (`2026-10-16-http-log-detail-compose-migration-design.md`, decision 5)
- E2E: yes.

**HTTP-10 — Search results**
- When a query of 2 or more characters is typed, Then:
  - All matches are highlighted.
  - A bar shows the bold counter "i / n" with up (previous) and down (next) buttons that wrap around.
  - About 600 ms later the list scrolls to the first match, and the current match gets a distinct highlight.
- With no match, or a query under 2 characters, the bar is hidden.
- Old: `P/present/http/items/HttpDetailContentItemView.kt:82-121,156-173,254-298`
- New: `P/present/http/HttpDetailContentPage.kt:96-111,159-197,364-366`
- Status: SAME
- E2E: yes. Type a key that occurs twice and assert "1 / 2"; tap down and assert "2 / 2", then "1 / 2".

**HTTP-11 — Literal search**
- Old: the query was compiled as a case-insensitive **regex**: ".." matched everything, and "(" crashed.
- New: literal, case-insensitive matching.
- Old: `P/present/http/SearchHighlightUtil.kt` (`indicesOf`, `Pattern.compile`)
- New: `P/present/http/HttpBodyContent.kt:121-132`; `P/present/http/SearchHighlightUtil.kt:34-45`
- Status: CHANGED-INTENTIONALLY (commit `f81852a`)
- E2E: yes. Type `{"` and assert there is no crash and the counter shows a count.

**HTTP-12 — Highlight colours**
- Old: all matches yellow background with red text; current match green.
- New: theme colours (amber, and orange for the current match), underlined. The counter bar is dark green with
  white text (light theme).
- Status: CHANGED-INTENTIONALLY (`2026-10-17-design-system-fixes-design.md`, section 2)
- E2E: no (screenshot only).

**HTTP-13 — Rotation**
- Old: tab and query were not reliably restored.
- New: the selected tab, each tab's query and the current match position survive rotation.
- New: `P/present/http/HttpLogDetailScreen.kt:75`; `P/present/http/HttpDetailContentPage.kt:77,316-359`
- Status: CHANGED-INTENTIONALLY (spec 2026-10-16 http, decision 5 and "Verification")
- E2E: yes.

**HTTP-14 — Overflow menu**
- When ⋮ is tapped, Then a menu lists, in this order:
  1. "Share HAR (whole log) as json"
  2. "Share CURL command"
  3. "Copy HAR (whole log)"
  4. "Copy CURL command"
  5. "Copy response content"
  6. "Copy request content"
  7. "Save HAR as json"
- New: items also show leading icons. The old `PopupMenu` didn't show icons.
- Old: `R/menu/http_detail_menu.xml`; `P/present/http/HttpLogDetailActivity.kt:107-114`
- New: `P/present/http/HttpLogDetailScreen.kt:44-53,144-163`
- Status: SAME (icons: CHANGED-INTENTIONALLY, spec 2026-10-16 http decision 2)
- E2E: yes. Assert the 7 labels in order.

**HTTP-15 — Share HAR as JSON**
- Then the chooser "Share file through" opens with `Devin_<datetime>.json` (`application/json`) holding the HAR
  entry.
- Old: `P/present/http/HttpLogDetailActivity.kt:178-192`
- New: `P/present/http/HttpLogDetailActivity.kt:125-139`
- Status: SAME
- E2E: yes (chooser).

**HTTP-16 — Share cURL**
- Then the chooser "Share CURL command" opens with `text/plain` cURL text.
- Old: `P/present/http/HttpLogDetailActivity.kt:155-166`
- New: `P/present/http/HttpLogDetailActivity.kt:106-117`
- Status: SAME
- E2E: yes.

**HTTP-17 — Copy HAR**
- Then the HAR JSON is copied and the toast "Copied" shows.
- Old: `P/present/http/HttpLogDetailActivity.kt:194-204`
- New: `P/present/http/HttpLogDetailActivity.kt:141-147`
- Status: SAME
- E2E: yes.

**HTTP-18 — Copy cURL (menu)**
- Then the cURL command is copied and the toast "Copied" shows.
- Old: `P/present/http/HttpLogDetailActivity.kt:168-176`
- New: `P/present/http/HttpLogDetailActivity.kt:119-123`
- Status: SAME
- E2E: yes.

**HTTP-19 — Copy response content**
- Then the raw response body is copied and the toast "Copied" shows. If there is no body, the toast is
  "Response is null".
- Old: `P/present/http/HttpLogDetailActivity.kt:252-260`
- New: `P/present/http/HttpLogDetailActivity.kt:173,179-185`
- Status: SAME
- E2E: yes.

**HTTP-20 — Copy request content**
- Then the raw request body is copied and the toast "Copied" shows. If there is no body, the toast is "Response
  is null" (the same string is reused, in both versions).
- Old: `P/present/http/HttpLogDetailActivity.kt:262-270`
- New: `P/present/http/HttpLogDetailActivity.kt:174,179-185`
- Status: SAME
- E2E: yes (a GET request: assert the "Response is null" toast).

**HTTP-21 — Save HAR as JSON**
- Then the create-document picker opens (in a chooser titled "Save HAR as json") proposing
  `Devin_<datetime>.json`. Saving shows "Save done" (long), or "Something went wrong!" on failure. Cancelling
  shows nothing.
- Old: `P/present/http/HttpLogDetailActivity.kt:119-122,213-221`; `P/AppExt.kt:118-133`
- New: `P/present/http/HttpLogDetailActivity.kt:91-94,156-164`
- Status: SAME
- E2E: yes (DocumentsUI).

**HTTP-22 — Edge-to-edge**
- New: the tabs and menu are padded for the system bars, so they aren't under the status bar on edge-to-edge
  devices.
- New: `P/present/http/HttpLogDetailScreen.kt:78,88`
- Status: CHANGED-INTENTIONALLY (commit `7b210a3`)
- E2E: yes (screenshot on API 35).

## 11. Log notifications and deep link

**NOTIF-01 — Notification content**
- Given a client config `logNotifications:{enabled:true, groups:[{name:"Net", tags:["A"], color:"#FF0000"}]}`
  and notification permission, When a log with tag `A` is inserted, Then a notification is posted:
  - Title: "Net".
  - Text: the log value, cut to 160 characters with "…".
  - Accent: the group colour.
  - Channel: "Net (<clientId>)".
- It is debounced and updated per (client, group). A tag in no group (and no `"*"` group) posts nothing.
- Old/New: `P/notification/LatestLogNotificationObserver.kt:161-241` (identical file)
- Status: SAME
- E2E: yes. Read it with `adb shell dumpsys notification --noredact` or UiAutomator (system UI).

**NOTIF-02 — Tap opens the client and tag**
- When the notification is tapped, Then Starter opens for that client:
  - The password sheet shows if one is required; if a force update is pending, it is blocked.
  - Then the log list opens with the tag chip `A` selected and filtered.
  - If the tag isn't in the chip list yet, a chip for it is appended and selected.
- This works with one or many clients. The notification is auto-cancelled.
- Old: `P/present/StarterActivity.kt:195-200,225-238,307`; `P/present/LogActivity.kt:107,322-334`
- New: `P/present/StarterActivity.kt:160-165,190-203,237`; `P/present/LogActivity.kt:92,212-224`
- Status: SAME
- E2E: yes. Multiple clients: tap the notification and assert the list with chip "A" selected.

**NOTIF-03 — Target client missing**
- Given the notification's client no longer exists, When it is tapped, Then Starter behaves as a normal launch.
- Old/New: `P/notification/LogNotificationLaunchCoordinator.kt:49-60`
- Status: SAME
- E2E: yes.

## 12. Global

**GEN-01 — Theme persistence**
- Then the light/dark choice from "Toggle theme" applies to every screen at launch.
- Old/New: `P/arch/BaseActivity.kt:16-27`
- Status: SAME
- E2E: yes.

**GEN-02 — Colours, shapes and type**
- The palette now comes from `colors.xml`/`values-night` for both XML and Compose, with WCAG-AA contrast fixes:
  HTTP error status, the okhttp chip text, secondary text, search highlights and counter, the session pill, and
  JSON number/boolean colours. There is one shape scale, no text below 11 sp, and spacing on a 4 dp grid.
- New: `P/uikit/theme/Theme.kt`, `Color.kt`
- Status: CHANGED-INTENTIONALLY (`2026-10-17-design-system-fixes-design.md`; commit `72bd92e`)
- E2E: no (screenshot tests only).

---

## Summary

The count uses each item's primary status (142 items). An item marked "SAME (… CHANGED …)" counts as SAME;
LIST-22 counts as CHANGED-INTENTIONALLY.

| Status | Count |
|---|---|
| SAME | 108 |
| CHANGED-INTENTIONALLY | 25 |
| CHANGED-UNDOCUMENTED | 8 |
| MISSING-IN-NEW | 0 (ROW-04 and the LIST-22 clear button were fixed on 2026-10-20) |
| NEW-ONLY | 1 |

### ⚠ MISSING-IN-NEW and likely regressions

Each claim below was checked against the whole `present-app/src/main` tree of `compose-migration`.

**All four were fixed on 2026-10-20** (see each item's Status):
- ROW-04: `TextLogRow` uses `getFormattedWithMillis`.
- LIST-22: a trailing clear (×) button was added.
- LIST-17: the chip `LazyRow` key is qualified by index and type.
- LOGIN-02: `KeyboardType.Password` was restored.

The table below is kept as the original finding.

| ID | What is lost or broken | Evidence |
|---|---|---|
| **ROW-04** | Text log rows show `HH:mm:ss` without the `.SSS` milliseconds that 4.5.0 added. | Old `P/log/TextLogItem.kt:30` calls `getFormattedWithMillis`. New `P/present/logitem/TextLogRow.kt:59` calls `initIfNeed(...).getFormatted()`. The only remaining caller of `getFormattedWithMillis` is `P/present/LogDetailDestination.kt:25` (grep). |
| **LIST-22** (clear button) | The log-list search row has no clear (×) button; the old `SearchView` had one. | `P/present/logitem/SearchBarRow.kt:37-49` has no `trailingIcon`. The HTTP body search does have one (`HttpDetailContentPage.kt:148-154`). |
| **LIST-17** (undocumented) | Creating a custom filter whose title equals an existing chip id (an existing tag, `okhttp`, `Index`, or another custom title) is expected to **crash**: duplicate `LazyRow` key. The old UI showed a duplicate chip. | `P/present/FilterChipsRow.kt:55` uses `items(filters, key = { it.id })`. `ReaderViewModel.addFilter` appends without a de-dupe (`P/present/ReaderViewModel.kt:268`). `LogActivity.withNotificationTarget` only de-dupes against tag chips (`:212-216`). Found by code reading, not run. |
| **LOGIN-02** (documented, but suspect) | The password keyboard is now numeric (`NumberPassword`). 4.3.1 deliberately switched it to `textPassword`, so a non-numeric `logPassword` can't be typed. The commit's rationale ("as the original XML did") contradicts `origin/main`. | Old `R/layout/fragment_client_login.xml:32` (`textPassword`, commit `1cf25cf`). New `P/present/ClientLoginBottomSheet.kt:78`. Commit `858ddd6`. |

### CHANGED-INTENTIONALLY

| ID | Change | Source |
|---|---|---|
| ROW-05 | Tag chip hidden only on a tag filter; shown on "All" and custom filters (old: hidden on "All", inconsistently) | user decision 2026-10-20 |
| LOGIN-03 | Eye icon → "SHOW"/"HIDE" text button | spec 2026-08-29 |
| UPD-05 | Optional-update Download no longer routes to the log list behind the browser | `858ddd6` |
| LIST-05 | Chip row is always a single scrolling row (no 2-row grid when there are more than 4 chips) | spec 2026-08-30 decision 2 |
| LIST-15 | Empty filter dialog: red outline without the "tag or title required" text; whitespace-only rejected | spec 2026-08-28 |
| LIST-22 | Log search is an always-expanded text field (was an iconified SearchView) | spec 2026-08-30 decision 4 |
| ROW-03 | Verbose badge "VERBOSE" (was "INFO") | `72bd92e` |
| ROW-06 | Row message capped at 10 lines / 2,000 characters | `2848a5e` |
| DET-03 | Back arrow on the log detail page | spec 2026-10-15 decision 4 |
| DET-06 | Double tap opens one detail page | `4a74783` |
| EXP-03 | Dialog height may change between modes | spec 2026-10-16 export |
| EXP-06 | Zip shares use `application/zip` | spec 2026-10-16 export |
| EXP-09 | Days field filtered to digits | spec 2026-10-16 export |
| EXP-10 | Buttons disabled while exporting | spec 2026-10-16 export |
| EXP-11 | Save survives rotation while the picker is open | `a288209` |
| EXP-12 | Prepare error → toast, dialog stays open | spec 2026-10-16 export |
| SET-10 | Settings dialogs and typed text survive rotation | `ee484d6` |
| IMP-05 | Import with no logs closes with "Empty file text!"; spinner; no re-read on rotation | `74d56f4` |
| HTTP-02 | Load failure shows "Something went wrong!" instead of blank tabs | `f81852a` |
| HTTP-09 | Body search is an expanded field with a clear button | spec 2026-10-16 http decision 5 |
| HTTP-11 | Literal body search (was regex) | `f81852a` |
| HTTP-12 | New search highlight and counter colours | spec 2026-10-17 |
| HTTP-13 | Tab and query survive rotation | spec 2026-10-16 http |
| HTTP-22 | Edge-to-edge insets on HTTP detail | `7b210a3` |
| GEN-02 | Unified palette, contrast, shapes, type scale | spec 2026-10-17 / `72bd92e` |

Also intentional, inside items marked SAME:
- ROW-09: HTTP path max lines 2 → 3, and a domain cap (`2848a5e`).
- HTTP-14: menu icons visible.
- HTTP-07 / DET-07: chunking of long text.

### CHANGED-UNDOCUMENTED (need a decision)

| ID | Difference | Kind |
|---|---|---|
| START-09 | Zero clients now clears stale rows | fix |
| LOGIN-10 | Login sheet vanishes on rotation | edge |
| UPD-07 | Force dialog hides the optional prompt instead of stacking on it | edge |
| LIST-07 | Chips truly disabled while loading | minor |
| LIST-23 | Search text now applied to later pages | fix |
| ROW-07 | Unknown log level: no badge (old: placeholder "INFO") | edge |
| DET-05 | Tag/Time values now selectable | minor |
| HTTP-04 | Overview values now selectable | minor |

Also inside SAME items: LIST-24 pagination trigger without scrolling; ROW-11 image loading/failed text.

### E2E coverage, prioritized

1. **ROW-04**: row time milliseconds. A regression; cheap to assert with `_date` ending in `123`.
2. **LIST-17**: create a filter named like an existing tag. Expected crash.
3. **LOGIN-01/04/06/07/08 + LOGIN-02**: password gate, including an alphanumeric password.
4. **START-02/03/04/07**: client states and auto-route (the core entry flow).
5. **LIST-06, LIST-20/21, LIST-23**: filter selection, search debounce, and search plus pagination.
6. **NOTIF-02**: notification tap → client → tag chip selected (multi-client and new-tag cases).
7. **ROW-02/03/09/11/13, ROW-01**: each row type renders the expected text, using provider-injected data.
8. **DET-01/02/04, LIST-32**: detail content, copy format, and back preserving list state.
9. **LIST-09..13, LIST-11 persistence + SET-07**: chip menus, pin persistence, pinned tags in Settings.
10. **LIST-25/26/19**: refresh toasts, clear logs (verify via the provider), clear custom filters.
11. **EXP-04/05/06/07/11**: save and share (DocumentsUI), zip content, save across rotation.
12. **HTTP-03/05/06/08/10/11/14..21**: HTTP detail via sample-app traffic; search counter; the 7 menu actions.
13. **SET-02..06, SET-11**: retention settings persistence and effect.
14. **UPD-01** (footer text and link). UPD-03..06 only with seeded prefs.
