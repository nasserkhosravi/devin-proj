# AppMetrica Crash Reporting for present-app — Design

Date: 2026-10-03
Branch: `add_crash_reporter`

## Goal

Report crashes of the Devin viewer app (`present-app`) to an AppMetrica dashboard, so production crashes of the viewer itself become visible. Crash data only — no analytics/session/location tracking.

## Scope

- **In:** `present-app` only.
- **Out:** `devin`, `devin-no-op`, `devin-write-okhttp*`, `sample-app`, and any library module. The published write libraries must not gain an AppMetrica dependency.
- **Out:** native (NDK) crash reporting — present-app has no native code.
- **Out:** R8 mapping upload — `isMinifyEnabled = false` in release today. Revisit if minification is turned on (AppMetrica Gradle plugin handles mapping upload).

## Dependency

- `gradle/libs.versions.toml`:
  - `[versions] appmetrica = "8.5.1"` (latest confirmed in official docs as of 2026-10-03; 8.6.0 exists on GitHub but Maven Central availability unconfirmed).
  - `[libraries] appmetrica-analytics = { module = "io.appmetrica.analytics:analytics", version.ref = "appmetrica" }`
- `present-app/build.gradle.kts`: `implementation(libs.appmetrica.analytics)`.
- Risk: 8.x may require a newer Kotlin / compileSdk than the project's (Kotlin 1.9.0, compileSdk 35). If `assembleRelease` fails for that reason, fall back to the newest 7.x release that builds rather than bumping project-wide toolchain versions.

## API key wiring

The repo is public, so the key is never committed.

- Source, in priority order:
  1. `appmetrica.apiKey` in the root `local.properties` (already git-ignored).
  2. `APPMETRICA_API_KEY` environment variable (for CI).
  3. Otherwise empty string.
- `present-app/build.gradle.kts`:
  - `release`: `buildConfigField("String", "APPMETRICA_API_KEY", "\"<resolved key>\"")`
  - `debug`: `buildConfigField("String", "APPMETRICA_API_KEY", "\"\"")` — debug builds never report.
- A missing key must not fail the build; it only disables reporting.

## Initialization

New file `present-app/src/main/java/com/khosravi/devin/present/crash/CrashReporting.kt`:

```kotlin
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

`PresentApplication.onCreate()` calls `CrashReporting.init(this)` immediately after `super.onCreate()`, before Dagger and notification-observer setup, so crashes during the rest of startup are captured.

Rationale for disabling extra tracking: present-app displays other apps' logs; it should send only crash reports, nothing about usage.

AppMetrica's uncaught-exception handler chains to the previous default handler. present-app installs no handler of its own, so there is no conflict.

## Error handling

- Blank key → silent no-op.
- `AppMetrica.activate` is not wrapped in try/catch; a failure there is a programming/config error that should surface during verification.

## Changelog

Add an entry to `present-app/CHANGELOG.md` under the next version: "Crash reporting via AppMetrica (release builds only)."

## Verification

No unit tests (per AGENTS.md, tests only on explicit request).

1. `./gradlew :present-app:assembleDebug :present-app:assembleRelease` (JDK 17) succeeds, with and without a key in `local.properties`.
2. Debug build: confirm `BuildConfig.APPMETRICA_API_KEY` is empty and the app starts normally.
3. Release build with a real key: trigger a temporary forced crash, relaunch the app (AppMetrica sends crashes on next launch), and confirm it appears in the AppMetrica dashboard (manual, user-side). Remove the forced crash afterward.
