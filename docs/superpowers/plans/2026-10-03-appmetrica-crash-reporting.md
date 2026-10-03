# AppMetrica Crash Reporting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Report crashes of `present-app` (release builds only) to AppMetrica, with no other analytics tracking.

**Architecture:** Add the AppMetrica Analytics SDK to `present-app` only. The API key is resolved at build time from `local.properties` / env var into `BuildConfig.APPMETRICA_API_KEY` (release only; debug gets `""`). A `CrashReporting.init()` helper activates AppMetrica with crash reporting on and sessions/app-open/location tracking off, called first in `PresentApplication.onCreate()`.

**Tech Stack:** Kotlin 1.9.0, AGP 8.10, Gradle version catalog, `io.appmetrica.analytics:analytics:8.5.1`.

Spec: `docs/superpowers/specs/2026-10-03-appmetrica-crash-reporting-design.md`

**Project rules (AGENTS.md):** Do NOT commit unless the user explicitly says so. Do NOT add unit tests. All Gradle commands need JDK 17:
`JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | Declare `appmetrica` version + `appmetrica-analytics` library |
| `present-app/build.gradle.kts` | Modify | Add dependency; resolve API key; per-build-type `APPMETRICA_API_KEY` BuildConfig field |
| `present-app/src/main/java/com/khosravi/devin/present/crash/CrashReporting.kt` | Create | Activate AppMetrica crash-only when key present |
| `present-app/src/main/java/com/khosravi/devin/present/PresentApplication.kt` | Modify | Call `CrashReporting.init(this)` first |
| `present-app/CHANGELOG.md` | Modify | Changelog entry |

---

### Task 1: Add AppMetrica dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `present-app/build.gradle.kts` (dependencies block)

- [ ] **Step 1: Add version and library to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` after `json = "20231013"`:

```toml
appmetrica = "8.5.1"
```

Under `[libraries]` after the `json = ...` line:

```toml
appmetrica-analytics = { module = "io.appmetrica.analytics:analytics", version.ref = "appmetrica" }
```

- [ ] **Step 2: Add the dependency to present-app**

In `present-app/build.gradle.kts`, in `dependencies { }`, after `implementation(libs.gson)`:

```kotlin
    implementation(libs.appmetrica.analytics)
```

- [ ] **Step 3: Verify it compiles**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

If it fails due to Kotlin metadata version (e.g. "compiled with an incompatible version of Kotlin") or a minCompileSdk requirement from AppMetrica 8.x: change `appmetrica` to the newest `7.x` release on Maven Central (check https://central.sonatype.com/artifact/io.appmetrica.analytics/analytics/versions), re-run, and record the chosen version in the spec's Dependency section. Do NOT bump project-wide Kotlin/AGP/compileSdk.

---

### Task 2: Wire the API key into BuildConfig

**Files:**
- Modify: `present-app/build.gradle.kts` (top of file + `buildTypes`)

- [ ] **Step 1: Add key resolution at the top of the file**

At the very top of `present-app/build.gradle.kts`, before `plugins { }`:

```kotlin
import java.util.Properties
```

After the `plugins { }` block, before `android { }`:

```kotlin
val appMetricaApiKey: String = run {
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    localProperties.getProperty("appmetrica.apiKey")
        ?: System.getenv("APPMETRICA_API_KEY")
        ?: ""
}
```

- [ ] **Step 2: Add per-build-type BuildConfig fields**

Replace the existing `buildTypes { }` block with:

```kotlin
    buildTypes {
        debug {
            buildConfigField("String", "VERSION_NAME", versionName)
            buildConfigField("String", "APPMETRICA_API_KEY", "\"\"")
        }
        release {
            buildConfigField("String", "VERSION_NAME", versionName)
            buildConfigField("String", "APPMETRICA_API_KEY", "\"$appMetricaApiKey\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
```

- [ ] **Step 3: Verify the field is generated**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:generateDebugBuildConfig :present-app:generateReleaseBuildConfig
grep -r APPMETRICA_API_KEY present-app/build/generated/source/buildConfig/
```
Expected: both `debug/.../BuildConfig.java` and `release/.../BuildConfig.java` contain `APPMETRICA_API_KEY`; debug value is `""`. Release value is `""` unless a key is set in `local.properties` (do not print a real key into logs/chat).

---

### Task 3: Create CrashReporting initializer

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/crash/CrashReporting.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.crash

import android.app.Application
import com.khosravi.devin.present.BuildConfig
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

object CrashReporting {

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
    }
}
```

If any builder method does not exist in the resolved SDK version, check the SDK's `AppMetricaConfig.Builder` (IDE autocomplete or https://appmetrica.yandex.com/docs/en/sdk/android/analytics/android-operations) and use the equivalent; do not silently drop a "disable tracking" call without noting it.

---

### Task 4: Call it from PresentApplication

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/PresentApplication.kt`

- [ ] **Step 1: Add import and call**

Add import:

```kotlin
import com.khosravi.devin.present.crash.CrashReporting
```

Change `onCreate()` so the call comes right after `super.onCreate()`:

```kotlin
    override fun onCreate() {
        super.onCreate()
        CrashReporting.init(this)
        appComponent = DaggerAppComponent.builder()
            .context(this)
            .application(this)
            .build()

        latestLogNotificationObserver = LatestLogNotificationObserver(this).apply {
            register()
        }
    }
```

- [ ] **Step 2: Build both variants**

Run:
```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug :present-app:assembleRelease
```
Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Changelog

**Files:**
- Modify: `present-app/CHANGELOG.md`

- [ ] **Step 1: Add an Unreleased section above `## 4.5.0`**

```markdown
## Unreleased
- Report crashes via AppMetrica (release builds only; requires `appmetrica.apiKey` in `local.properties` or `APPMETRICA_API_KEY` env var).

```

---

### Task 6: Manual verification

- [ ] **Step 1: Debug build sanity** — install debug APK on an emulator, launch, confirm the app starts normally and logcat shows no AppMetrica activation (`adb logcat | grep -i appmetrica` → nothing from activation).

- [ ] **Step 2: Release crash check (user-side, needs real key)** — user adds `appmetrica.apiKey=<key>` to `local.properties`, builds/installs release, triggers a temporary forced crash (e.g. `throw RuntimeException("AppMetrica test crash")` in a click handler), relaunches the app so the crash is sent, and confirms it in the AppMetrica dashboard. Remove the forced crash afterward; do not leave it in the diff.

- [ ] **Step 3: Stop** — report results to the user. Do not commit until the user says to.
