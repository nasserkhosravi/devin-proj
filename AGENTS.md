# Agent Rules

- Do not commit code until the user explicitly says to commit.
- Do not add or update (unit) tests until the user explicitly says to.

# Project Map

Devin: Android logging framework. Write-side library apps embed to produce logs, and a standalone presenter app that reads/displays/exports them.

| Module | Type | Purpose |
|---|---|---|
| `devin` | Android library | Core write module (Room DB, `DevinContentProvider`, logging APIs). Published to Maven Central. |
| `devin-no-op` | Android library | No-op stub of `devin`, same API, empty bodies. Used in release builds for zero overhead. |
| `devin-write-okhttp` | Android library | OkHttp interceptor, captures HTTP traffic as HAR-format logs. |
| `devin-write-okhttp-no-op` | Android library | No-op stub of the above. |
| `present-app` | Android app | The "Devin" viewer app — reads/displays/exports logs from all registered clients. |
| `sample-app` | Android app | Demo/integration-test app for the write + OkHttp libraries. |
| `lib-calendar` | Android library | Gregorian + Persian (Jalali) calendar support. |
| `lib-har` | Kotlin/JVM library | HAR 1.2 spec models + JSON converter. |

`docs/superpowers/specs/` and `docs/superpowers/plans/` hold design docs and implementation plans for past/ongoing features — check there before re-deriving design intent from scratch.

# Gotchas

- **Gradle needs JDK 17, not the system default.** `./gradlew` fails under JDK 24 (`Could not create an instance of type org.gradle.api.reporting.internal.DefaultReportContainer`) with this project's Gradle/AGP versions. Prefix commands with `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home` (or whatever JDK 17 install exists on the machine).
- **`present-app` hosts the shared `DevinContentProvider`, not each client app.** The `<provider android:authorities="com.khosravi.devin.provider">` component is only declared in `present-app`'s manifest. Client apps (e.g. `sample-app`) only declare a `<queries><provider .../></queries>` visibility entry and write into present-app's provider via `ContentResolver` — there's one shared Room DB, owned by present-app's process.
- **`org.json.JSONObject` throws in plain Android unit tests.** The Android stub jar on the unit-test classpath throws `RuntimeException` on every `org.json.*` call unless a real `org.json:json` jar is added as a `testImplementation` dependency (shadows the stub) — see `present-app/build.gradle.kts`. `testOptions.unitTests.isReturnDefaultValues = true` is also set as a defensive fallback for other incidentally-stubbed Android APIs (e.g. `android.util.Log`).
- **Per-tag-group notifications** (`present-app/src/main/java/com/khosravi/devin/present/notification/LatestLogNotificationObserver.kt`): clients opt in via `presenterConfig.logNotifications = {enabled, groups: [{name, tags}]}`. No implicit catch-all group — a tag not listed in any group produces no notification. Each `(clientId, groupName)` pair debounces and publishes independently (see `docs/superpowers/specs/2026-08-23-notification-groups-design.md`).
