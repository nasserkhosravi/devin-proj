# Log Data Retention — Design

Date: 2026-10-13
Branch: `data_retention`

## Problem

Devin never deletes logs on its own. A heavy user, or a client that logs frequently (e.g. OkHttp HAR entries, whose `_meta` holds the full HAR JSON), grows the shared Room DB (`db_devin`, owned by present-app's process) without bound. The only cleanup is the manual per-client "clear logs" (`LogDao.removeLogs`).

Two existing leaks make it worse:

1. **Orphaned content files.** `DevinContentProvider.writeContentFileIfItsPossible` stores binary payloads at `filesDir/<clientId>/<logId>/file_content`. Nothing ever deletes them — not even the manual clear. (Note: in practice the provider currently writes these under log id `0`, or fails to write them because it uses `mkdir()` instead of `mkdirs()`, so few exist today. That's a separate `devin` bug; the sweep still handles both the `0` dir and correctly-named dirs once it's fixed.)
2. **The DB file never shrinks.** SQLite reuses freed pages but does not return them to the OS without `VACUUM`.

## Decisions

- **Policy:** age + count, per client. A log is deleted if it is older than the max age, **or** if it falls outside the newest N logs of its client (ordered by `_date`).
- **Owner:** the present-app user only, via a global setting. Clients have no say (no `presenterConfig` key). A client-supplied override can be added later as a new `presenterConfig` key without changing this design.
- **Defaults:** enabled, 7 days, 10,000 logs per client.
- **Trigger:** throttled, on log insert only. No WorkManager job, no manual "clean up now" action.
- **Scope:** `present-app` only. No changes to `devin` (write), `devin-no-op`, or the OkHttp modules.

## Why present-app can do this alone

- `DevinContentProvider` is declared only in present-app's manifest, so every client write starts present-app's process and `PresentApplication.onCreate()` always runs.
- Every insert notifies `DevinUriHelper.getLogChangeUri(id, clientId, tag)`, a descendant of `getLogListUri()`. `LatestLogNotificationObserver` already consumes this; a second observer can reuse the same signal.
- The Room DB and the content files live in present-app's process and `filesDir`. present-app reaches the DB directly via the public `DevinDB.getInstance(context).openHelper.writableDatabase`, using `LogTable` column constants so a column rename breaks compilation rather than silently breaking retention.
- The generated `DevinDB_Impl` enables `PRAGMA foreign_keys`, so `meta_index` rows go with their log via the existing `ON DELETE CASCADE`.

Trade-off: present-app now depends on the `log` table schema directly, not only on the provider contract. Acceptable because both live in this repo and the constants keep it compile-checked.

## Components (all in `present-app`)

### `RetentionPolicy`

```kotlin
data class RetentionPolicy(
    val isEnabled: Boolean,
    val maxAgeDays: Int,
    val maxCountPerClient: Int,
)
```

### `AppPref` additions

- `retentionEnabled: Boolean` (default `true`)
- `retentionDays: Int` (default `7`)
- `retentionMaxCount: Int` (default `10_000`)
- `retentionPolicy: RetentionPolicy` — read-only convenience that bundles the three.
- `lastVacuumAt: Long` (default `0`) — used by `RetentionCleaner` to limit `VACUUM` to once per 24 h.

### `RetentionCleaner`

Pure data work, no Android lifecycle. Takes the `SupportSQLiteDatabase`, the `filesDir`, and a clock (`() -> Long`) so it is unit-testable later.

`trim(policy, lastVacuumAt): TrimResult` runs, in order (the caller persists `lastVacuumAt` when `TrimResult.vacuumed` is true):

1. **Row deletion, in batches of 5,000 rows per short transaction** so a large first trim never holds the DB long enough to stall client apps' synchronous `insert` calls (often on their main thread):
   - Age: repeat `DELETE FROM log WHERE _id IN (SELECT _id FROM log WHERE _date < :cutoff LIMIT :batch)` until nothing is deleted.
   - Count, per client (client IDs from `SELECT DISTINCT _clientId FROM log`): skip the client if `COUNT(*)` ≤ the cap (index-only); otherwise delete the oldest excess rows by `_date`, in batches.
   - Not atomic as a whole: a failure leaves earlier batches committed, and the next trim continues. Every batch is correct on its own.
2. **Orphan file sweep:** for each client id in the `client` table (only those — other `filesDir` entries are never touched), for every `filesDir/<clientId>/<logId>/` directory where `<logId>` parses as a `Long` and no `log` row with that `_id` exists, delete the directory recursively. Directories whose name is not a log id are left alone. This also cleans files left behind by the manual clear and by any data from before this feature.
3. **Conditional `VACUUM` (fallback):** Android's SQLite usually defaults to `auto_vacuum=FULL`, which already returns freed pages to the OS, so this rarely fires. It runs only if at least 24 h have passed since the last vacuum (timestamp in `AppPref`), and `freelist_count / page_count > 0.25`, and free bytes (`freelist_count * page_size`) > 1 MB.
4. Returns `TrimResult(deletedRows, deletedFileDirs, vacuumed)`.

### `RetentionObserver`

A `ContentObserver` registered in `PresentApplication.onCreate()` on `getLogListUri()` with `notifyForDescendants = true`, next to `LatestLogNotificationObserver`.

- `onChange(uri)`: ignore unless `DevinUriHelper.getLogChange(uri) != null` (individual inserts/updates only).
- Read `appPref.retentionPolicy`; return immediately if disabled.
- **Throttle:** in-memory `lastTrimAt`. Skip if `now - lastTrimAt < 10 min`. `lastTrimAt` starts at `0`, so the first write after process start always trims.
- Run `RetentionCleaner.trim` on a single-thread `Executor` so trims never overlap and never block inserts or the main thread.
- It is a Dagger `@Singleton` (exposed on `AppComponent`) so `SettingsActivity` can reach the same instance to reset the throttle.
- `resetThrottle()`: called when a retention setting changes, so the next write applies the new policy.

### Settings screen

`SettingsActivity`, built with XML + ViewBinding (not Compose: this branch is based on `main`, and the Compose chain `compose_4.4.0` is behind `main`; it can be migrated with the rest of the app later). No ViewModel — it only reads/writes `AppPref` and computes one storage number. Opened from a new "Settings" item in `LogActivity`'s overflow menu (`R.menu.main_menu`).

| Control | Values |
|---|---|
| Auto-delete old logs | switch |
| Keep logs for | 1 / 3 / 7 / 14 / 30 days |
| Max logs per app | 1,000 / 5,000 / 10,000 / 50,000 |
| Storage used | read-only: DB file size (`db_devin` + `-wal` + `-shm`) + total size of client content dirs in `filesDir`, computed off the main thread |

The pickers are disabled while the switch is off. Each change writes `AppPref` and calls `RetentionObserver.resetThrottle()`.

## Edge cases and error handling

- **Imported logs:** `ImportLogActivity` parses files into memory and never writes to the DB, so retention never touches them.
- **Clock:** all clients stamp `_date` with `Date()` on the same device, so they share a clock. Logs dated in the future (clock set ahead, then corrected) aren't removed by age until that date passes, but the count cap still bounds them. No special handling.
- **Trim while browsing:** only the oldest logs (the end of the newest-first list) are deleted, so pages already loaded stay valid. `LogActivity` doesn't observe the provider today (new logs appear on Refresh), so trimmed logs likewise disappear on the next refresh — no extra notification is sent. Detail screens don't crash if their log is trimmed: `LogItemDetailActivity` renders from intent extras, and `HttpLogDetailActivity` ignores a `null` result from `getHttpLog` (blank screen). Improving that blank state is out of scope.
- **Disabled:** the observer exits before any DB work.
- **First trim after upgrade:** with defaults on, existing users with large DBs get one larger delete + `VACUUM` on the first client write. It runs off the main thread in short batches, so client inserts interleave instead of stalling. The deleted count is logged.
- **Partial failure:** row deletion is batched; a failure keeps already-committed batches and the next trim resumes. The file sweep and `VACUUM` run afterwards and only log on failure; the next trim's sweep picks up leftovers. Exceptions are caught, logged, and reported via a new `CrashReporting.reportError(message, throwable)` (no-op when AppMetrica isn't activated); they never propagate into the provider or crash the process.
- **Process death:** throttle state is in memory and resets, which only means the next write trims.

## Testing

Per `AGENTS.md`, no unit tests are added unless the user asks. `RetentionCleaner` takes its DB, directory, and clock as parameters, so it can be unit-tested later.

Manual verification with `sample-app`:

1. Set max count to 1,000, flood logs, confirm the client's log count settles at 1,000 after the next throttle window.
2. Set retention to 1 day, back-date some rows' `_date` (via `adb shell` + `sqlite3` on a debuggable build), trigger a write, confirm they're gone.
3. Confirm orphaned `filesDir/<clientId>/<logId>` directories are removed, including ones left behind by the manual "clear logs".
4. Confirm the `db_devin` file shrinks after a large trim (`VACUUM`).
5. Toggle the switch off, flood logs, confirm nothing is deleted.

## Out of scope

- Per-client limits, or client-supplied limits through `presenterConfig`.
- A size-based (MB) cap.
- Periodic background cleanup (WorkManager) and a manual "clean up now" action.
- Changes to the `devin` write library, including fixing `removeLogs` to delete content files there (the orphan sweep covers it from the presenter side).
