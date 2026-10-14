# Log Data Retention Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically delete old logs (age + per-client count cap) and orphaned content files from present-app's storage, with a Settings screen to configure it.

**Architecture:** Everything lives in `present-app`; the `devin` write library is untouched. A `RetentionObserver` (a `ContentObserver` on the log list URI, like `LatestLogNotificationObserver`) fires on every log insert, throttles to one trim per 10 minutes, and runs `RetentionCleaner` on a single background thread. `RetentionCleaner` talks directly to the shared Room DB via `DevinDB.getInstance(context).openHelper.writableDatabase` using `LogTable`/`ClientTable` column constants. Limits are stored in `AppPref` and edited in a new XML/ViewBinding `SettingsActivity`.

**Tech Stack:** Kotlin 1.9, AndroidX Room 2.5.1 (`SupportSQLiteDatabase`), Dagger 2, Material Components 1.9.0, ViewBinding, AppMetrica (error reporting).

**Spec:** `docs/superpowers/specs/2026-10-13-data-retention-design.md`

---

## Ground rules for this repo (read first)

- **No commits.** `AGENTS.md`: do not commit until the user explicitly says so. Tasks end with a build check, not a commit.
- **No tests.** `AGENTS.md`: do not add or update unit tests unless the user explicitly asks. Verification is a compile check per task plus a manual on-device pass in the last task.
- **Gradle needs JDK 17.** Every Gradle command in this plan is prefixed with
  `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`.
- **Branch:** `data_retention` (based on `main`). Not a Compose branch — use XML + ViewBinding for UI.
- **Do not modify** anything under `devin/`, `devin-no-op/`, `devin-write-okhttp*/`.

## File map

| File | Status | Responsibility |
|---|---|---|
| `present-app/src/main/java/com/khosravi/devin/present/retention/RetentionPolicy.kt` | Create | Policy data class, defaults, picker option lists |
| `present-app/src/main/java/com/khosravi/devin/present/data/AppPref.kt` | Modify | Persist retention settings + last vacuum time |
| `present-app/src/main/java/com/khosravi/devin/present/crash/CrashReporting.kt` | Modify | Add `reportError` for non-fatal errors |
| `present-app/src/main/java/com/khosravi/devin/present/retention/RetentionCleaner.kt` | Create | SQL deletes, orphan file sweep, conditional VACUUM |
| `present-app/src/main/java/com/khosravi/devin/present/retention/RetentionObserver.kt` | Create | Insert observer, throttle, background executor |
| `present-app/src/main/java/com/khosravi/devin/present/retention/StorageUsage.kt` | Create | Compute DB + content-file size for the Settings screen |
| `present-app/src/main/java/com/khosravi/devin/present/di/AppComponent.kt` | Modify | Expose `RetentionObserver`, inject `SettingsActivity` |
| `present-app/src/main/java/com/khosravi/devin/present/PresentApplication.kt` | Modify | Register `RetentionObserver` |
| `present-app/src/main/res/layout/activity_settings.xml` | Create | Settings UI |
| `present-app/src/main/res/values/strings.xml` | Modify | Settings strings + plurals |
| `present-app/src/main/java/com/khosravi/devin/present/present/settings/SettingsActivity.kt` | Create | Settings screen logic |
| `present-app/src/main/AndroidManifest.xml` | Modify | Declare `SettingsActivity` |
| `present-app/src/main/res/menu/main_menu.xml` | Modify | "Settings" overflow item |
| `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt` | Modify | Open `SettingsActivity` from the menu |

---

### Task 1: `RetentionPolicy` + `AppPref` fields

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/retention/RetentionPolicy.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/data/AppPref.kt`

- [ ] **Step 1: Create `RetentionPolicy.kt`**

```kotlin
package com.khosravi.devin.present.retention

/**
 * How long logs are kept in the shared Devin DB. A log is deleted when it is older than
 * [maxAgeDays] **or** falls outside the newest [maxCountPerClient] logs of its client.
 */
data class RetentionPolicy(
    val isEnabled: Boolean,
    val maxAgeDays: Int,
    val maxCountPerClient: Int,
) {
    companion object {
        val DEFAULT = RetentionPolicy(isEnabled = true, maxAgeDays = 7, maxCountPerClient = 10_000)

        /** Values offered by the Settings screen pickers. */
        val AGE_OPTIONS_DAYS = listOf(1, 3, 7, 14, 30)
        val COUNT_OPTIONS = listOf(1_000, 5_000, 10_000, 50_000)
    }
}
```

- [ ] **Step 2: Add retention properties to `AppPref`**

In `AppPref.kt`, add the import at the top with the others:

```kotlin
import com.khosravi.devin.present.retention.RetentionPolicy
```

Add these members right after the `theme` property (before `lastUpdateCheckAt`):

```kotlin
    var retentionEnabled: Boolean
        get() = pref.getBoolean(KEY_RETENTION_ENABLED, RetentionPolicy.DEFAULT.isEnabled)
        set(value) = pref.edit { putBoolean(KEY_RETENTION_ENABLED, value) }

    var retentionDays: Int
        get() = pref.getInt(KEY_RETENTION_DAYS, RetentionPolicy.DEFAULT.maxAgeDays)
        set(value) = pref.edit { putInt(KEY_RETENTION_DAYS, value) }

    var retentionMaxCount: Int
        get() = pref.getInt(KEY_RETENTION_MAX_COUNT, RetentionPolicy.DEFAULT.maxCountPerClient)
        set(value) = pref.edit { putInt(KEY_RETENTION_MAX_COUNT, value) }

    val retentionPolicy: RetentionPolicy
        get() = RetentionPolicy(retentionEnabled, retentionDays, retentionMaxCount)

    /** Epoch millis of the last successful `VACUUM` run by retention; `0` if never. */
    var lastVacuumAt: Long
        get() = pref.getLong(KEY_LAST_VACUUM_AT, 0L)
        set(value) = pref.edit { putLong(KEY_LAST_VACUUM_AT, value) }
```

Add these constants at the end of the `companion object` (after `KEY_NOTIFIED_UPDATE_VERSION`):

```kotlin
        private const val KEY_RETENTION_ENABLED = "RETENTION_ENABLED"
        private const val KEY_RETENTION_DAYS = "RETENTION_DAYS"
        private const val KEY_RETENTION_MAX_COUNT = "RETENTION_MAX_COUNT"
        private const val KEY_LAST_VACUUM_AT = "LAST_VACUUM_AT"
```

- [ ] **Step 3: Compile**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

### Task 2: `CrashReporting.reportError`

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/crash/CrashReporting.kt`

AppMetrica is only activated when an API key is configured (release builds on CI). Reporting must be a no-op otherwise.

- [ ] **Step 1: Replace the file contents**

```kotlin
package com.khosravi.devin.present.crash

import android.app.Application
import com.khosravi.devin.present.BuildConfig
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

object CrashReporting {

    @Volatile
    private var isActivated = false

    fun init(application: Application) {
        val apiKey = BuildConfig.APPMETRICA_API_KEY
        if (apiKey.isBlank()) return

        val config = AppMetricaConfig.newConfigBuilder(apiKey)
            .withCrashReporting(true)
            .withNativeCrashReporting(false)
            .withSessionsAutoTrackingEnabled(false)
            .withAppOpenTrackingEnabled(false)
            .withLocationTracking(false)
            .build()
        AppMetrica.activate(application, config)
        isActivated = true
    }

    /** Reports a caught, non-fatal error. No-op when AppMetrica isn't activated (e.g. debug builds). */
    fun reportError(message: String, error: Throwable) {
        if (!isActivated) return
        AppMetrica.reportError(message, error)
    }
}
```

- [ ] **Step 2: Compile**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. If `AppMetrica.reportError(String, Throwable)` doesn't resolve, check the AppMetrica 8.5.1 API (`io.appmetrica.analytics.AppMetrica`) for the non-fatal error method and use it — do not add a new dependency.

---

### Task 3: `RetentionCleaner`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/retention/RetentionCleaner.kt`

Context the implementer needs:
- Table/column constants: `LogTable.TABLE_NAME` (`"log"`), `LogTable.COLUMN_ID` (`_id`), `LogTable.COLUMN_DATE` (`_date`, epoch millis), `LogTable.COLUMN_CLIENT_ID` (`_clientId`); `ClientTable.TABLE_NAME` (`"client"`), `ClientTable.COLUMN_PACKAGE_ID`. All in `com.khosravi.devin.write.room` (the `devin` module, already a present-app dependency).
- `meta_index` rows are removed by `ON DELETE CASCADE` (Room enables `PRAGMA foreign_keys`), so no explicit delete is needed.
- Content files live at `filesDir/<clientId>/<logId>/file_content` (written by `DevinContentProvider.writeContentFileIfItsPossible`).
- `SupportSQLiteDatabase.delete(table, whereClause, whereArgs: Array<Any?>)` returns the deleted row count; `Long` args are bound as integers.

- [ ] **Step 1: Create `RetentionCleaner.kt`**

```kotlin
package com.khosravi.devin.present.retention

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.khosravi.devin.write.room.ClientTable
import com.khosravi.devin.write.room.LogTable
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Applies a [RetentionPolicy] to the shared Devin DB and the per-log content files.
 *
 * No Android lifecycle here: the DB, the files dir and the clock are injected so this can be
 * unit-tested later. Row deletion throws on failure (caller handles it); the file sweep and
 * `VACUUM` are best-effort and only log, since the next trim retries them.
 */
class RetentionCleaner(
    private val db: SupportSQLiteDatabase,
    private val filesDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {

    data class TrimResult(
        val deletedRows: Int,
        val deletedContentDirs: Int,
        val vacuumed: Boolean,
    )

    /** @param lastVacuumAt epoch millis of the previous `VACUUM`; caller persists the new time when [TrimResult.vacuumed]. */
    fun trim(policy: RetentionPolicy, lastVacuumAt: Long): TrimResult {
        val deletedRows = deleteExpiredRows(policy)
        val deletedDirs = try {
            sweepOrphanContentDirs()
        } catch (e: Exception) {
            Log.w(TAG, "Orphan content sweep failed", e)
            0
        }
        val vacuumed = try {
            vacuumIfWorthIt(lastVacuumAt)
        } catch (e: Exception) {
            Log.w(TAG, "VACUUM failed", e)
            false
        }
        return TrimResult(deletedRows, deletedDirs, vacuumed)
    }

    private fun deleteExpiredRows(policy: RetentionPolicy): Int {
        db.beginTransaction()
        try {
            val cutoff = now() - TimeUnit.DAYS.toMillis(policy.maxAgeDays.toLong())
            var deleted = db.delete(LogTable.TABLE_NAME, "$COL_DATE < ?", arrayOf<Any?>(cutoff))
            for (clientId in queryLogClientIds()) {
                deleted += db.delete(
                    LogTable.TABLE_NAME,
                    "$COL_CLIENT_ID = ? AND $COL_ID IN (" +
                        "SELECT $COL_ID FROM ${LogTable.TABLE_NAME} WHERE $COL_CLIENT_ID = ? " +
                        "ORDER BY $COL_DATE DESC LIMIT -1 OFFSET ?)",
                    arrayOf<Any?>(clientId, clientId, policy.maxCountPerClient.toLong()),
                )
            }
            db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    /** Deletes `filesDir/<clientId>/<logId>/` dirs whose log row no longer exists. Only registered clients' dirs are scanned. */
    private fun sweepOrphanContentDirs(): Int {
        var deleted = 0
        for (clientId in queryRegisteredClientIds()) {
            val logDirs = File(filesDir, clientId).listFiles { file -> file.isDirectory } ?: continue
            for (logDir in logDirs) {
                val logId = logDir.name.toLongOrNull() ?: continue
                if (!logExists(logId) && logDir.deleteRecursively()) {
                    deleted++
                }
            }
        }
        return deleted
    }

    private fun vacuumIfWorthIt(lastVacuumAt: Long): Boolean {
        if (now() - lastVacuumAt < VACUUM_INTERVAL_MILLIS) return false
        val pageCount = pragmaLong("page_count")
        if (pageCount == 0L) return false
        val freePages = pragmaLong("freelist_count")
        val freeBytes = freePages * pragmaLong("page_size")
        if (freePages.toDouble() / pageCount <= VACUUM_MIN_FREE_RATIO) return false
        if (freeBytes <= VACUUM_MIN_FREE_BYTES) return false
        db.execSQL("VACUUM")
        // In WAL mode the shrink only reaches the main DB file after a checkpoint.
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        return true
    }

    private fun queryLogClientIds(): List<String> =
        queryStrings("SELECT DISTINCT $COL_CLIENT_ID FROM ${LogTable.TABLE_NAME}")

    private fun queryRegisteredClientIds(): List<String> =
        queryStrings("SELECT ${ClientTable.COLUMN_PACKAGE_ID} FROM ${ClientTable.TABLE_NAME}")

    private fun queryStrings(sql: String): List<String> = db.query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let(::add)
            }
        }
    }

    private fun logExists(logId: Long): Boolean =
        db.query("SELECT 1 FROM ${LogTable.TABLE_NAME} WHERE $COL_ID = ? LIMIT 1", arrayOf<Any?>(logId))
            .use { it.moveToFirst() }

    private fun pragmaLong(name: String): Long =
        db.query("PRAGMA $name").use { if (it.moveToFirst()) it.getLong(0) else 0L }

    private companion object {
        const val TAG = "RetentionCleaner"
        const val COL_ID = LogTable.COLUMN_ID
        const val COL_DATE = LogTable.COLUMN_DATE
        const val COL_CLIENT_ID = LogTable.COLUMN_CLIENT_ID

        val VACUUM_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)
        const val VACUUM_MIN_FREE_RATIO = 0.25
        const val VACUUM_MIN_FREE_BYTES = 1L * 1024 * 1024
    }
}
```

- [ ] **Step 2: Compile**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. If `androidx.sqlite.db.SupportSQLiteDatabase` doesn't resolve, it's transitively provided by `libs.room.runtime` (already in `present-app/build.gradle.kts`) — check the import, don't add a dependency.

---

### Task 4: `RetentionObserver` + registration

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/retention/RetentionObserver.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/di/AppComponent.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/PresentApplication.kt`

Context: `DevinContentProvider.insert()`/`update()` call `notifyChange(DevinUriHelper.getLogChangeUri(id, clientId, tag))`, a descendant of `DevinUriHelper.getLogListUri()`. `DevinUriHelper.getLogChange(uri)` returns non-null only for those per-log URIs. `Context` is bound in the Dagger graph via `AppComponent.Builder.context(...)`; `AppPref` has an `@Inject` constructor.

- [ ] **Step 1: Create `RetentionObserver.kt`**

```kotlin
package com.khosravi.devin.present.retention

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.khosravi.devin.present.crash.CrashReporting
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.room.DevinDB
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers [RetentionCleaner] off the back of log inserts. Any client write starts present-app's
 * process (it hosts `DevinContentProvider`), so this sees every insert without a background job.
 *
 * Trims are throttled to one per [THROTTLE_MILLIS]; the first insert after process start always
 * trims. Work runs on a single-thread executor so trims never overlap or block the provider.
 */
@Singleton
class RetentionObserver @Inject constructor(
    context: Context,
    private val appPref: AppPref,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val lastTrimAt = AtomicLong(0L)

    fun register() {
        appContext.contentResolver.registerContentObserver(DevinUriHelper.getLogListUri(), true, this)
    }

    /** Makes the next insert trim immediately; call after the user changes a retention setting. */
    fun resetThrottle() {
        lastTrimAt.set(0L)
    }

    override fun onChange(selfChange: Boolean) {
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        if (uri == null || DevinUriHelper.getLogChange(uri) == null) return
        val policy = appPref.retentionPolicy
        if (!policy.isEnabled) return
        val now = System.currentTimeMillis()
        val last = lastTrimAt.get()
        if (now - last < THROTTLE_MILLIS) return
        if (!lastTrimAt.compareAndSet(last, now)) return
        executor.execute { trim(policy) }
    }

    private fun trim(policy: RetentionPolicy) {
        try {
            val db = DevinDB.getInstance(appContext).openHelper.writableDatabase
            val result = RetentionCleaner(db, appContext.filesDir).trim(policy, appPref.lastVacuumAt)
            if (result.vacuumed) {
                appPref.lastVacuumAt = System.currentTimeMillis()
            }
            Log.d(TAG, "Retention trim done: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Retention trim failed", e)
            CrashReporting.reportError("Retention trim failed", e)
        }
    }

    private companion object {
        const val TAG = "RetentionObserver"
        val THROTTLE_MILLIS = TimeUnit.MINUTES.toMillis(10)
    }
}
```

- [ ] **Step 2: Expose it on `AppComponent`**

In `AppComponent.kt`, add the import:

```kotlin
import com.khosravi.devin.present.retention.RetentionObserver
```

and add below `fun updateChecker(): UpdateChecker`:

```kotlin
    fun retentionObserver(): RetentionObserver
```

- [ ] **Step 3: Register in `PresentApplication`**

In `PresentApplication.onCreate()`, add right after the `latestLogNotificationObserver = ... register() }` block:

```kotlin
        appComponent.retentionObserver().register()
```

(No field needed — Dagger's `@Singleton` keeps the instance alive for the component's lifetime, and `SettingsActivity` gets the same instance.)

- [ ] **Step 4: Compile**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL` (kapt regenerates `DaggerAppComponent`).

---

### Task 5: `StorageUsage`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/retention/StorageUsage.kt`

- [ ] **Step 1: Create `StorageUsage.kt`**

```kotlin
package com.khosravi.devin.present.retention

import android.content.Context
import androidx.annotation.WorkerThread
import com.khosravi.devin.present.data.ClientContentProvider
import com.khosravi.devin.write.room.DevinDB
import java.io.File

object StorageUsage {

    /** Bytes used by the Devin DB (incl. WAL/SHM) plus all registered clients' content files. */
    @WorkerThread
    fun computeBytes(context: Context): Long {
        val dbName = DevinDB.getInstance(context).openHelper.databaseName ?: return 0L
        val dbPath = context.getDatabasePath(dbName).path
        val dbBytes = listOf("", "-wal", "-shm").sumOf { suffix -> File(dbPath + suffix).length() }
        val contentBytes = ClientContentProvider.getClientList(context).sumOf { client ->
            File(context.filesDir, client.packageId).walkBottomUp()
                .filter { it.isFile }
                .sumOf { it.length() }
        }
        return dbBytes + contentBytes
    }
}
```

- [ ] **Step 2: Compile**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

### Task 6: Settings layout + strings

**Files:**
- Create: `present-app/src/main/res/layout/activity_settings.xml`
- Modify: `present-app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings**

In `strings.xml`, add before the closing `</resources>`:

```xml
    <string name="menu_settings">Settings</string>
    <string name="settings_title">Settings</string>
    <string name="settings_section_storage">Storage</string>
    <string name="settings_retention_enabled">Auto-delete old logs</string>
    <string name="settings_retention_enabled_summary">Keeps storage small by removing old logs automatically</string>
    <string name="settings_keep_logs_for">Keep logs for</string>
    <string name="settings_max_logs_per_client">Max logs per app</string>
    <string name="settings_storage_used">Storage used</string>
    <string name="settings_storage_calculating">Calculating…</string>
    <plurals name="settings_retention_days">
        <item quantity="one">%d day</item>
        <item quantity="other">%d days</item>
    </plurals>
```

- [ ] **Step 2: Create `activity_settings.xml`**

`?attr/homeAsUpIndicator` gives the platform back arrow (there's no back-arrow drawable in this module). Spacing attrs `?attr/spaceLarge` / `?attr/spaceSmall` are defined in `res/values/dimens.xml` + `themes.xml`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <androidx.appcompat.widget.Toolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="?attr/actionBarSize"
        app:navigationIcon="?attr/homeAsUpIndicator"
        app:title="@string/settings_title" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingStart="?attr/spaceLarge"
                android:paddingTop="?attr/spaceLarge"
                android:paddingEnd="?attr/spaceLarge"
                android:paddingBottom="?attr/spaceSmall"
                android:text="@string/settings_section_storage"
                android:textAppearance="?attr/textAppearanceTitleSmall"
                android:textColor="?attr/colorPrimary" />

            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switchRetention"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingStart="?attr/spaceLarge"
                android:paddingTop="?attr/spaceSmall"
                android:paddingEnd="?attr/spaceLarge"
                android:paddingBottom="?attr/spaceSmall"
                android:text="@string/settings_retention_enabled"
                android:textAppearance="?attr/textAppearanceBodyLarge" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingStart="?attr/spaceLarge"
                android:paddingEnd="?attr/spaceLarge"
                android:paddingBottom="?attr/spaceSmall"
                android:text="@string/settings_retention_enabled_summary"
                android:textAppearance="?attr/textAppearanceBodySmall" />

            <LinearLayout
                android:id="@+id/rowKeepDays"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:orientation="vertical"
                android:padding="?attr/spaceLarge">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_keep_logs_for"
                    android:textAppearance="?attr/textAppearanceBodyLarge" />

                <TextView
                    android:id="@+id/tvKeepDaysValue"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    tools:text="7 days" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/rowMaxCount"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:orientation="vertical"
                android:padding="?attr/spaceLarge">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_max_logs_per_client"
                    android:textAppearance="?attr/textAppearanceBodyLarge" />

                <TextView
                    android:id="@+id/tvMaxCountValue"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    tools:text="10,000" />
            </LinearLayout>

            <com.google.android.material.divider.MaterialDivider
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="?attr/spaceLarge">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_storage_used"
                    android:textAppearance="?attr/textAppearanceBodyLarge" />

                <TextView
                    android:id="@+id/tvStorageValue"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_storage_calculating"
                    android:textAppearance="?attr/textAppearanceBodyMedium" />
            </LinearLayout>
        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

- [ ] **Step 3: Check resources compile**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:processDebugResources
```
Expected: `BUILD SUCCESSFUL`. If a `?attr/textAppearance...` attribute isn't found, the app theme is `Theme.Material3.Light.NoActionBar` (see `res/values/themes.xml`), which defines all of them — check spelling.

---

### Task 7: `SettingsActivity`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/settings/SettingsActivity.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/di/AppComponent.kt`
- Modify: `present-app/src/main/AndroidManifest.xml`

Context: `BaseActivity` (`arch/BaseActivity.kt`) is a `CoroutineScope by MainScope()` and exposes an injected `appPref`. Subclasses call `getAppComponent().inject(this)` **before** `super.onCreate()` (see `LogItemDetailActivity`).

- [ ] **Step 1: Create `SettingsActivity.kt`**

```kotlin
package com.khosravi.devin.present.present.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.khosravi.devin.present.R
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.databinding.ActivitySettingsBinding
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.retention.RetentionObserver
import com.khosravi.devin.present.retention.RetentionPolicy
import com.khosravi.devin.present.retention.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import javax.inject.Inject

class SettingsActivity : BaseActivity() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding: ActivitySettingsBinding
        get() = _binding!!

    @Inject
    lateinit var retentionObserver: RetentionObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        _binding = ActivitySettingsBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.switchRetention.isChecked = appPref.retentionEnabled
        binding.switchRetention.setOnCheckedChangeListener { _, isChecked ->
            appPref.retentionEnabled = isChecked
            onRetentionChanged()
        }
        binding.rowKeepDays.setOnClickListener { showKeepDaysPicker() }
        binding.rowMaxCount.setOnClickListener { showMaxCountPicker() }

        bindRetentionValues()
        loadStorageUsage()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun onRetentionChanged() {
        retentionObserver.resetThrottle()
        bindRetentionValues()
    }

    private fun bindRetentionValues() {
        val isEnabled = appPref.retentionEnabled
        binding.tvKeepDaysValue.text = formatDays(appPref.retentionDays)
        binding.tvMaxCountValue.text = formatCount(appPref.retentionMaxCount)
        listOf(binding.rowKeepDays, binding.rowMaxCount).forEach { it.setEnabledLook(isEnabled) }
    }

    private fun showKeepDaysPicker() {
        val options = RetentionPolicy.AGE_OPTIONS_DAYS
        showPicker(
            titleRes = R.string.settings_keep_logs_for,
            labels = options.map(::formatDays),
            checkedIndex = options.indexOf(appPref.retentionDays),
        ) { index ->
            appPref.retentionDays = options[index]
            onRetentionChanged()
        }
    }

    private fun showMaxCountPicker() {
        val options = RetentionPolicy.COUNT_OPTIONS
        showPicker(
            titleRes = R.string.settings_max_logs_per_client,
            labels = options.map(::formatCount),
            checkedIndex = options.indexOf(appPref.retentionMaxCount),
        ) { index ->
            appPref.retentionMaxCount = options[index]
            onRetentionChanged()
        }
    }

    private fun showPicker(titleRes: Int, labels: List<String>, checkedIndex: Int, onPicked: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                onPicked(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun loadStorageUsage() {
        launch {
            val bytes = withContext(Dispatchers.IO) { StorageUsage.computeBytes(applicationContext) }
            _binding?.tvStorageValue?.text = Formatter.formatShortFileSize(this@SettingsActivity, bytes)
        }
    }

    private fun formatDays(days: Int): String =
        resources.getQuantityString(R.plurals.settings_retention_days, days, days)

    private fun formatCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)

    private fun View.setEnabledLook(isEnabled: Boolean) {
        this.isEnabled = isEnabled
        alpha = if (isEnabled) 1f else DISABLED_ALPHA
    }

    companion object {
        private const val DISABLED_ALPHA = 0.38f

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
```

- [ ] **Step 2: Add an inject method to `AppComponent`**

In `AppComponent.kt`, add the import:

```kotlin
import com.khosravi.devin.present.present.settings.SettingsActivity
```

and add after `fun inject(activity: LogItemDetailActivity)`:

```kotlin
    fun inject(activity: SettingsActivity)
```

- [ ] **Step 3: Declare the activity in the manifest**

In `present-app/src/main/AndroidManifest.xml`, add after the `.present.LogItemDetailActivity` `<activity>` element:

```xml
        <activity
            android:name=".present.settings.SettingsActivity"
            android:exported="false" />
```

- [ ] **Step 4: Compile**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

---

### Task 8: Open Settings from `LogActivity`

**Files:**
- Modify: `present-app/src/main/res/menu/main_menu.xml`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt`

- [ ] **Step 1: Add the menu item**

In `main_menu.xml`, add after the `action_change_theme` item (last item, before `</menu>`):

```xml
    <item
        android:id="@+id/action_settings"
        android:title="@string/menu_settings" />
```

- [ ] **Step 2: Handle it in `LogActivity.onOptionsItemSelected`**

Add the import:

```kotlin
import com.khosravi.devin.present.present.settings.SettingsActivity
```

In `onOptionsItemSelected`, add a branch after the `R.id.action_change_theme -> { ... }` branch and before `else ->`:

```kotlin
            R.id.action_settings -> {
                startActivity(SettingsActivity.intent(this))
                true
            }
```

- [ ] **Step 3: Build the APK**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug :sample-app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

---

### Task 9: Manual verification on the emulator

No code changes. Use the running emulator (typically `emulator-5554`; check `adb devices`). present-app's package is `com.khosravi.devin.present`; the DB is `databases/db_devin` inside it.

- [ ] **Step 1: Install both apps**

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:installDebug :sample-app:installDebug
```

- [ ] **Step 2: Settings screen**

Open present-app → pick a client → overflow menu → **Settings**. Check:
- Switch on, "Keep logs for: 7 days", "Max logs per app: 10,000", storage size shown (not stuck on "Calculating…").
- Turning the switch off dims both rows and makes them non-clickable; turning it back on restores them.
- Each picker shows its options with the current value checked; choosing one updates the row text.
- Values survive leaving and reopening the screen. Back arrow closes the screen.

- [ ] **Step 3: Count cap**

Set "Max logs per app" to 1,000. Use sample-app to produce more than 1,000 logs. Because Settings reset the throttle, the next insert trims. Check the count:

```bash
adb shell "run-as com.khosravi.devin.present sqlite3 databases/db_devin \"SELECT _clientId, COUNT(*) FROM log GROUP BY _clientId;\""
```
Expected: sample-app's client has at most 1,000 rows (plus any inserted since the trim, until the next 10-minute window). `adb logcat -s RetentionObserver` shows `Retention trim done: TrimResult(deletedRows=...)`.
If `sqlite3` isn't available on the emulator image, pull the DB instead: `adb exec-out run-as com.khosravi.devin.present cat databases/db_devin > /tmp/db_devin` (also pull `-wal`) and query locally.

- [ ] **Step 4: Age rule**

Back-date some rows and trigger a trim:

```bash
adb shell "run-as com.khosravi.devin.present sqlite3 databases/db_devin \"UPDATE log SET _date = _date - 30*86400000 WHERE _id IN (SELECT _id FROM log ORDER BY _id LIMIT 50);\""
```
In Settings, set "Keep logs for" to 7 days (re-select it to reset the throttle), then produce one log from sample-app. Expected: those 50 rows are gone (`SELECT COUNT(*) FROM log WHERE _date < strftime('%s','now')*1000 - 7*86400000;` → `0`).

- [ ] **Step 5: Orphan content sweep**

```bash
adb shell "run-as com.khosravi.devin.present ls files/"
adb shell "run-as com.khosravi.devin.present ls files/<sample-app-package>"
```
Use LogActivity's "Clear all logs" for sample-app (this leaves content dirs behind today), toggle a Settings value to reset the throttle, produce one log, then list again. Expected: only the dir for the new log (if it has content) remains.

- [ ] **Step 6: VACUUM shrinks the file**

Note the "Storage used" value in Settings. Clear `lastVacuumAt` so the 24 h gate doesn't block: `adb shell "run-as com.khosravi.devin.present sed -i '/LAST_VACUUM_AT/d' shared_prefs/AppPref.xml"`, then force-stop present-app (`adb shell am force-stop com.khosravi.devin.present`) so prefs reload. Set "Max logs per app" to 1,000 when the DB holds well over that (e.g. 10,000+ rows from sample-app), produce one log, and reopen Settings. Expected: logcat shows `vacuumed=true` and "Storage used" dropped. (If the freed space is under 1 MB or 25% of the file, `vacuumed=false` is correct — flood more logs first.)

- [ ] **Step 7: Disabled**

Turn the switch off, set nothing else, flood logs from sample-app. Expected: no `Retention trim done` lines in logcat and the count keeps growing.

- [ ] **Step 8: Report to the user**

Summarize what passed/failed with evidence (counts, logcat lines). Do **not** commit — ask the user whether to commit.
