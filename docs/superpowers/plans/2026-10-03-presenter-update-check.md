# Presenter Update Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell `present-app` users when a newer presenter APK is published on GitHub Releases, with no backend of our own.

**Architecture:** An `UpdateChecker` (Dagger singleton) fetches `GET https://api.github.com/repos/nasserkhosravi/devin-proj/releases` at most once per 24h, picks the highest-versioned *presenter* release (tag parses as a version **and** has an `.apk` asset), and caches it in `AppPref`. If it is newer than `BuildConfig.VERSION_NAME`, the user is told once per version: a system notification if notifications are allowed, otherwise a dialog on `LogActivity`. The `StarterActivity` footer permanently shows "Update available" while one exists. The check can be switched off from the `LogActivity` menu.

**Tech Stack:** Kotlin, `HttpURLConnection` (no new dependency), `org.json`, coroutines, Dagger 2, `NotificationCompat`, `SharedPreferences` (`AppPref`).

---

## Background the implementer needs

- **Why not `/releases/latest`:** that endpoint returns the most recent release of *any* module. It currently returns `write/v4.3.1`. Library releases share this repo.
- **Existing tag shapes** (from the GitHub API on 2026-10-03):
  - Presenter: `presenter_4.4.0`, `presenter_4.3.1`, `Presenter/4.1.0`, `4.0.0`, `3.1.0`.
  - Library/other: `write/v4.3.1`, `write_4.2.0`, `okhttp-write/v1.0.2`, `v1.1.0`, `3.1.1` (no APK).
  - Rule used: the tag matches `^(presenter[_/-]?)?v?X.Y.Z$` (case-insensitive) **and** the release has at least one asset ending in `.apk`. This accepts every presenter release and rejects every library release above.
- **Release convention going forward:** tag `presenter_X.Y.Z`, attach the APK, and keep `versionName` in `present-app/build.gradle.kts` in `X.Y.Z` form.
- **GitHub limits:** unauthenticated calls allow 60 requests/hour/IP and need a `User-Agent` header. Checking once a day is far below the limit.
- **Process start ≠ user opened app.** `present-app` hosts `DevinContentProvider`, so client apps writing logs also start its process and `PresentApplication.onCreate`. That is why the check is throttled and the notification is the main channel.
- **minSdk is 24.** `NotificationChannel` is API 26+, so use `NotificationChannelCompat` / `NotificationManagerCompat`.
- **Build needs JDK 17:** prefix Gradle with `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`.
- **AGENTS.md rules:** do **not** add tests and do **not** commit unless the user explicitly says so. This plan therefore has no test-writing steps and no commit steps. Verification is by build plus a manual run (Task 8).

## File structure

All paths are under `present-app/src/main/`.

| File | Action | Responsibility |
|---|---|---|
| `java/com/khosravi/devin/present/update/AppVersion.kt` | Create | Parse `X.Y.Z` / presenter tags; compare versions |
| `java/com/khosravi/devin/present/update/GitHubReleaseSource.kt` | Create | HTTP call + JSON → `ReleaseInfo`; release-page URL constant |
| `java/com/khosravi/devin/present/update/UpdateNotifier.kt` | Create | Post the "update available" system notification; build the open-release intent |
| `java/com/khosravi/devin/present/update/UpdateChecker.kt` | Create | Throttle, cache, decide "available" / "should prompt" |
| `java/com/khosravi/devin/present/data/AppPref.kt` | Modify | Persist enabled flag, last-check time, latest release, last-prompted version |
| `java/com/khosravi/devin/present/di/AppModule.kt` | Modify | Provide `UpdateChecker` singleton |
| `java/com/khosravi/devin/present/di/AppComponent.kt` | Modify | Expose `updateChecker()` for `PresentApplication` |
| `java/com/khosravi/devin/present/PresentApplication.kt` | Modify | Kick off the check on process start |
| `java/com/khosravi/devin/present/present/LogActivity.kt` | Modify | Dialog on resume; menu toggle |
| `java/com/khosravi/devin/present/present/StarterActivity.kt` | Modify | Footer shows the available update |
| `res/menu/main_menu.xml` | Modify | Checkable "Check for updates" item |
| `res/values/strings.xml` | Modify | New strings |
| `present-app/CHANGELOG.md` | Modify | Unreleased entry |

---

### Task 1: `AppVersion`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/update/AppVersion.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.khosravi.devin.present.update

/**
 * A `major.minor.patch` version. [parse] accepts plain versions (`4.5.0`, `v4.5.0`) and the
 * presenter release tag shapes used on GitHub (`presenter_4.4.0`, `Presenter/4.1.0`).
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VERSION_PATTERN =
            Regex("^(?:presenter[_/-]?)?v?(\\d+)\\.(\\d+)\\.(\\d+)$", RegexOption.IGNORE_CASE)

        fun parse(value: String): AppVersion? {
            val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
            )
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 2: `GitHubReleaseSource`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/update/GitHubReleaseSource.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.khosravi.devin.present.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: AppVersion, val pageUrl: String)

/**
 * Reads presenter releases from GitHub. The repo also publishes library releases (`write/v…`,
 * `okhttp-write/v…`), so `/releases/latest` is not usable; we list releases and keep only ones
 * whose tag parses as an [AppVersion] and that ship an `.apk` asset.
 */
class GitHubReleaseSource(private val currentVersionName: String) {

    /** Blocking; call off the main thread. Returns null on any non-200 response. */
    fun fetchLatestPresenterRelease(): ReleaseInfo? {
        val connection = (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Devin-Presenter/$currentVersionName")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseLatestPresenterRelease(body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RELEASES_PAGE_URL = "https://github.com/nasserkhosravi/devin-proj/releases"
        private const val RELEASES_API_URL =
            "https://api.github.com/repos/nasserkhosravi/devin-proj/releases?per_page=30"
        private const val TIMEOUT_MILLIS = 10_000

        fun parseLatestPresenterRelease(json: String): ReleaseInfo? {
            val releases = JSONArray(json)
            return (0 until releases.length())
                .map { releases.getJSONObject(it) }
                .filterNot { it.optBoolean("draft") || it.optBoolean("prerelease") }
                .filter { it.hasApkAsset() }
                .mapNotNull { release ->
                    val version = AppVersion.parse(release.optString("tag_name")) ?: return@mapNotNull null
                    ReleaseInfo(version, release.optString("html_url").ifEmpty { RELEASES_PAGE_URL })
                }
                .maxByOrNull { it.version }
        }

        private fun JSONObject.hasApkAsset(): Boolean {
            val assets = optJSONArray("assets") ?: return false
            return (0 until assets.length()).any {
                assets.getJSONObject(it).optString("name").endsWith(".apk", ignoreCase = true)
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Check the parsing rule against real data**

Run:
```bash
curl -s "https://api.github.com/repos/nasserkhosravi/devin-proj/releases?per_page=30" | python3 -c "import json,sys,re; p=re.compile(r'^(?:presenter[_/-]?)?v?(\d+)\.(\d+)\.(\d+)$',re.I); print([r['tag_name'] for r in json.load(sys.stdin) if p.match(r['tag_name']) and any(a['name'].lower().endswith('.apk') for a in r['assets'])])"
```
Expected: only presenter tags, for example `['presenter_4.4.0', 'presenter_4.3.1', 'presenter_4.3.0', 'presenter_4.2.0', 'Presenter/4.1.0', '4.0.0', '3.1.0', '3.0.0', '2.0.0']`. No `write…`, `okhttp…`, `v1.1.0` or `3.1.1`.

---

### Task 3: `AppPref` storage

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/data/AppPref.kt`

- [ ] **Step 1: Add the properties** directly after the existing `theme` property:

```kotlin
    var isUpdateCheckEnabled: Boolean
        get() = pref.getBoolean(KEY_UPDATE_CHECK_ENABLED, true)
        set(value) = pref.edit { putBoolean(KEY_UPDATE_CHECK_ENABLED, value) }

    var lastUpdateCheckAt: Long
        get() = pref.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)
        set(value) = pref.edit { putLong(KEY_LAST_UPDATE_CHECK_AT, value) }

    var latestReleaseVersion: String?
        get() = pref.getString(KEY_LATEST_RELEASE_VERSION, null)
        set(value) = pref.edit { putString(KEY_LATEST_RELEASE_VERSION, value) }

    var latestReleaseUrl: String?
        get() = pref.getString(KEY_LATEST_RELEASE_URL, null)
        set(value) = pref.edit { putString(KEY_LATEST_RELEASE_URL, value) }

    var promptedUpdateVersion: String?
        get() = pref.getString(KEY_PROMPTED_UPDATE_VERSION, null)
        set(value) = pref.edit { putString(KEY_PROMPTED_UPDATE_VERSION, value) }
```

- [ ] **Step 2: Add the keys** to the existing `companion object`:

```kotlin
        private const val KEY_UPDATE_CHECK_ENABLED = "UPDATE_CHECK_ENABLED"
        private const val KEY_LAST_UPDATE_CHECK_AT = "LAST_UPDATE_CHECK_AT"
        private const val KEY_LATEST_RELEASE_VERSION = "LATEST_RELEASE_VERSION"
        private const val KEY_LATEST_RELEASE_URL = "LATEST_RELEASE_URL"
        private const val KEY_PROMPTED_UPDATE_VERSION = "PROMPTED_UPDATE_VERSION"
```

---

### Task 4: Strings

**Files:**
- Modify: `present-app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the strings** before `</resources>`:

```xml
    <string name="update_notification_channel_name">App updates</string>
    <string name="update_available_title">Devin update available</string>
    <string name="update_available_message">Version %1$s is available (you have %2$s).</string>
    <string name="update_action_download">Download</string>
    <string name="update_action_later">Later</string>
    <string name="menu_check_for_updates">Check for updates</string>
    <string name="starter_footer_update">v%1$s by %2$s · Update available: v%3$s</string>
```

---

### Task 5: `UpdateNotifier`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/update/UpdateNotifier.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.khosravi.devin.present.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.R

class UpdateNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    /** @return true if the notification was posted. */
    @SuppressLint("MissingPermission")
    fun notify(update: ReleaseInfo): Boolean {
        if (!canPostNotifications()) return false
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(appContext.getString(R.string.update_notification_channel_name))
                .build()
        )
        val contentIntent = PendingIntent.getActivity(
            appContext,
            CONTENT_INTENT_REQUEST_CODE,
            openReleaseIntent(update),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(appContext.getString(R.string.update_available_title))
            .setContentText(
                appContext.getString(R.string.update_available_message, update.version.toString(), BuildConfig.VERSION_NAME)
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        return try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Notification permission was revoked before the update notification could be posted", exception)
            false
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return notificationManager.areNotificationsEnabled()
    }

    companion object {
        private const val TAG = "UpdateNotifier"
        private const val CHANNEL_ID = "app_update"
        private const val NOTIFICATION_ID = 2001
        private const val CONTENT_INTENT_REQUEST_CODE = 2001

        fun openReleaseIntent(update: ReleaseInfo): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
```

`R.drawable.ic_notification_logo` already exists (used by `LatestLogNotificationObserver`). It is currently untracked in git, so make sure it is included when this work is committed.

---

### Task 6: `UpdateChecker` + DI + process-start trigger

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/update/UpdateChecker.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/di/AppModule.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/di/AppComponent.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/PresentApplication.kt`

- [ ] **Step 1: Create `UpdateChecker.kt`**

```kotlin
package com.khosravi.devin.present.update

import android.content.Context
import android.util.Log
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.data.AppPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.hours

/**
 * Checks GitHub for a newer presenter release at most once per [CHECK_INTERVAL_MILLIS] and
 * caches the result in [AppPref]. The user is prompted once per new version: through
 * [UpdateNotifier] when notifications are allowed, otherwise by the caller of [takePendingPrompt]
 * (a dialog in `LogActivity`). The throttle timestamp is only stored on success, so an offline
 * device retries on the next trigger instead of waiting a full day.
 */
class UpdateChecker(
    context: Context,
    private val appPref: AppPref,
    private val releaseSource: GitHubReleaseSource = GitHubReleaseSource(BuildConfig.VERSION_NAME),
    private val currentVersion: AppVersion? = AppVersion.parse(BuildConfig.VERSION_NAME),
) {

    private val notifier = UpdateNotifier(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isChecking = AtomicBoolean(false)

    fun checkInBackgroundIfDue() {
        if (!appPref.isUpdateCheckEnabled) return
        if (System.currentTimeMillis() - appPref.lastUpdateCheckAt < CHECK_INTERVAL_MILLIS) return
        if (!isChecking.compareAndSet(false, true)) return
        scope.launch {
            try {
                val release = releaseSource.fetchLatestPresenterRelease() ?: return@launch
                appPref.lastUpdateCheckAt = System.currentTimeMillis()
                appPref.latestReleaseVersion = release.version.toString()
                appPref.latestReleaseUrl = release.pageUrl
                val update = availableUpdate() ?: return@launch
                if (isNotPrompted(update) && notifier.notify(update)) {
                    markPrompted(update)
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Update check failed", exception)
            } finally {
                isChecking.set(false)
            }
        }
    }

    /** The cached newer release, or null if none, unknown, or the check is disabled. */
    fun availableUpdate(): ReleaseInfo? {
        if (!appPref.isUpdateCheckEnabled) return null
        val current = currentVersion ?: return null
        val latest = appPref.latestReleaseVersion?.let(AppVersion::parse) ?: return null
        if (latest <= current) return null
        return ReleaseInfo(latest, appPref.latestReleaseUrl ?: GitHubReleaseSource.RELEASES_PAGE_URL)
    }

    /** Returns the update once per version, marking it prompted; null afterwards. */
    fun takePendingPrompt(): ReleaseInfo? {
        val update = availableUpdate() ?: return null
        if (!isNotPrompted(update)) return null
        markPrompted(update)
        return update
    }

    private fun isNotPrompted(update: ReleaseInfo) = appPref.promptedUpdateVersion != update.version.toString()

    private fun markPrompted(update: ReleaseInfo) {
        appPref.promptedUpdateVersion = update.version.toString()
    }

    companion object {
        private const val TAG = "UpdateChecker"
        private val CHECK_INTERVAL_MILLIS = 24.hours.inWholeMilliseconds
    }
}
```

- [ ] **Step 2: Provide it from `AppModule.kt`.** Add these imports:

```kotlin
import android.content.Context
import com.khosravi.devin.present.data.AppPref
import com.khosravi.devin.present.update.UpdateChecker
```

Then add this method inside `class AppModule`:

```kotlin
    @Singleton
    @Provides
    fun updateChecker(context: Context, appPref: AppPref): UpdateChecker = UpdateChecker(context, appPref)
```

- [ ] **Step 3: Expose it on `AppComponent.kt`.** Add `import com.khosravi.devin.present.update.UpdateChecker`, then add this method inside the interface, after `fun inject(dialog: LogExportDialog)`:

```kotlin
    fun updateChecker(): UpdateChecker
```

- [ ] **Step 4: Trigger on process start in `PresentApplication.kt`.** Append to the end of `onCreate()`, after the `latestLogNotificationObserver` block:

```kotlin
        appComponent.updateChecker().checkInBackgroundIfDue()
```

- [ ] **Step 5: Compile**

Run: `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (Dagger/kapt regenerates `DaggerAppComponent`)

---

### Task 7: UI: `LogActivity` dialog + menu toggle, `StarterActivity` footer

**Files:**
- Modify: `present-app/src/main/res/menu/main_menu.xml`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt`
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/StarterActivity.kt`

- [ ] **Step 1: Menu item.** In `main_menu.xml`, add this after the `action_change_theme` item:

```xml
    <item
        android:id="@+id/action_toggle_update_check"
        android:checkable="true"
        android:title="@string/menu_check_for_updates" />
```

- [ ] **Step 2: `LogActivity`: inject the checker.** Add these imports:

```kotlin
import androidx.appcompat.app.AlertDialog
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.update.UpdateChecker
import com.khosravi.devin.present.update.UpdateNotifier
```

Skip any import that is already present. Then add this next to the other `@Inject` fields:

```kotlin
    @Inject
    lateinit var updateChecker: UpdateChecker
```

`LogActivity` is already injected via `AppComponent.inject(activity: LogActivity)`, so no component change is needed.

- [ ] **Step 3: `LogActivity`: prompt on resume.** Add `onResume` and a helper next to `onCreateOptionsMenu`:

```kotlin
    override fun onResume() {
        super.onResume()
        updateChecker.checkInBackgroundIfDue()
        showUpdatePromptIfNeeded()
    }

    private fun showUpdatePromptIfNeeded() {
        val update = updateChecker.takePendingPrompt() ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(getString(R.string.update_available_message, update.version.toString(), BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.update_action_download) { _, _ ->
                startActivity(UpdateNotifier.openReleaseIntent(update))
            }
            .setNegativeButton(R.string.update_action_later, null)
            .show()
    }
```

- [ ] **Step 4: `LogActivity`: menu check state and toggle.** Add `onPrepareOptionsMenu` after `onCreateOptionsMenu`:

```kotlin
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_toggle_update_check)?.isChecked = appPref.isUpdateCheckEnabled
        return super.onPrepareOptionsMenu(menu)
    }
```

Then add this branch in `onOptionsItemSelected`, just before `else ->`:

```kotlin
            R.id.action_toggle_update_check -> {
                appPref.isUpdateCheckEnabled = !appPref.isUpdateCheckEnabled
                item.isChecked = appPref.isUpdateCheckEnabled
                updateChecker.checkInBackgroundIfDue()
                true
            }
```

`appPref` is the public `@Inject lateinit var` inherited from `BaseActivity`.

- [ ] **Step 5: `StarterActivity`: footer.** Add these imports:

```kotlin
import com.khosravi.devin.present.update.GitHubReleaseSource
import com.khosravi.devin.present.update.UpdateChecker
```

Then add this next to the other `@Inject` fields:

```kotlin
    @Inject
    lateinit var updateChecker: UpdateChecker
```

In `onCreate`, **delete** these lines:

```kotlin
        binding.tvFooter.text = getString(R.string.starter_footer, BuildConfig.VERSION_NAME, CONTRIBUTOR_NAME)
        binding.tvFooter.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
        }
```

Add `onResume` and `bindFooter`:

```kotlin
    override fun onResume() {
        super.onResume()
        bindFooter()
    }

    private fun bindFooter() {
        val update = updateChecker.availableUpdate()
        binding.tvFooter.text = if (update == null) {
            getString(R.string.starter_footer, BuildConfig.VERSION_NAME, CONTRIBUTOR_NAME)
        } else {
            getString(R.string.starter_footer_update, BuildConfig.VERSION_NAME, CONTRIBUTOR_NAME, update.version.toString())
        }
        val url = update?.pageUrl ?: GitHubReleaseSource.RELEASES_PAGE_URL
        binding.tvFooter.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
```

Remove the now-unused `RELEASES_URL` constant from the `companion object`.

- [ ] **Step 6: Build the APK**

Run: `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, with no new warnings about unused imports or the removed constant.

---

### Task 8: Manual verification on a device or emulator

- [ ] **Step 1: Fake an old version.** In `present-app/build.gradle.kts`, temporarily change `val versionName = "\"4.5.0\""` to `"\"4.0.0\""`, then build and install:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:installDebug
```

- [ ] **Step 2: Clear data and launch** so the throttle and prompted state reset:

```bash
adb shell pm clear com.khosravi.devin.present && adb shell monkey -p com.khosravi.devin.present 1
```

Expected:
- With notifications **granted**: within a few seconds, a "Devin update available: Version 4.4.0 is available (you have 4.0.0)." notification appears. Tapping it opens the `presenter_4.4.0` release page. No dialog follows.
- With notifications **denied**: no notification. On the next `LogActivity` resume (background the app and return), the dialog shows once. "Download" opens the release page. It does not show again.
- The `StarterActivity` footer reads `v4.0.0 by nasser.khosravi · Update available: v4.4.0` (reach it by having 0 or 2+ clients, or pressing back from `LogActivity`).

- [ ] **Step 3: Throttle and once-per-version.** Kill and relaunch the app.
  - Expected: no second notification or dialog.
  - `adb logcat -s UpdateChecker` shows no failures.

- [ ] **Step 4: Opt-out.** In `LogActivity`'s overflow menu, uncheck "Check for updates".
  - Expected: the footer reverts to the plain version text.
  - After `adb shell pm clear` + relaunch the setting resets to on (pref cleared), which is expected.

- [ ] **Step 5: Offline.** Enable airplane mode, `pm clear`, relaunch.
  - Expected: no crash, and `logcat -s UpdateChecker` shows one "Update check failed" warning.
  - Disable airplane mode and relaunch: the check runs again (the throttle was not stamped on failure).

- [ ] **Step 6: Real version.** Revert `versionName` to `"\"4.5.0\""`, reinstall, `pm clear`, launch.
  - Expected: no notification or dialog, because 4.4.0 < 4.5.0.

- [ ] **Step 7: Confirm the revert:** `git diff present-app/build.gradle.kts` must not show the `4.0.0` change.

---

### Task 9: Changelog + release convention

**Files:**
- Modify: `present-app/CHANGELOG.md`

- [ ] **Step 1:** Under `## Unreleased`, add:

```markdown
- Notify when a newer presenter release is published on GitHub (checked at most once a day; notification, or a one-time dialog if notifications are off; footer shows the available version). Can be turned off from the log screen menu ("Check for updates").
```

- [ ] **Step 2: Remind the user** (do not automate) of the release convention this feature now depends on: tag presenter releases `presenter_X.Y.Z`, attach the `.apk` to the GitHub Release, and keep `versionName` as `X.Y.Z`. A release without an APK asset, or with a draft/pre-release flag, is ignored.

---

## Out of scope (YAGNI)

- In-app APK download/install (`REQUEST_INSTALL_PACKAGES`)
- WorkManager periodic checks: process start and `LogActivity.onResume` are enough, because client log writes also start the process
- Release-notes text in the prompt
- A "check now" button

## Notes for the executor

- **Unit tests:** `AppVersion.parse` and `GitHubReleaseSource.parseLatestPresenterRelease` are pure and would be the natural targets. `org.json:json` is already a `testImplementation`, so `JSONArray` works in JVM tests. Per AGENTS.md, **do not add them unless the user asks**.
- **Commits:** do not commit until the user explicitly says so.
