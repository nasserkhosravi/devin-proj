# Agent Rules

- Do not commit code until the user explicitly says to commit.
- Do not add or update (unit) tests until the user explicitly says to.
- Follow the release flow below for every branch, merge, version bump, and CHANGELOG edit. If you don't know which release a change targets, ask; don't guess.

# Release Flow

## Components

Each component is released on its own. Its name is used in its release branch and its tag.

| Component | Modules | Version lives in | CHANGELOG | Tag | Release workflow |
|---|---|---|---|---|---|
| `presenter` | `present-app` | `versionName` (and `versionCode`) in `present-app/build.gradle.kts` | `present-app/CHANGELOG.md` | `presenter_X.Y.Z` | `release-presenter.yml` |
| `write` | `devin-write`, `devin-no-op` | `POM_VERSION_NAME` in both modules' `gradle.properties` (must match) | `devin-write/CHANGELOG.md` | `write_X.Y.Z` | `release-library.yml` → `write` |
| `write-okhttp` | `devin-write-okhttp`, `devin-write-okhttp-no-op` | `POM_VERSION_NAME` in both modules' `gradle.properties` (must match) | `devin-write-okhttp/CHANGELOG.md` | `write-okhttp_X.Y.Z` | `release-library.yml` → `write-okhttp` |
| `lib-har` | `lib-har` | `POM_VERSION_NAME` in `lib-har/gradle.properties` | `lib-har/CHANGELOG.md` | `lib-har_X.Y.Z` | `release-library.yml` → `lib-har` |

## Branches

- **`main`**: the released code. Only release branches are merged into it. Never merge or commit a feature, fix, or any other branch into `main` directly.
- **Release branch**: `<component>-release-X.Y.Z`, e.g. `presenter-release-4.6.0`, `write-release-4.5.0`, `write-okhttp-release-1.0.3`, `lib-har-release-1.0.1`.
  - Create it from the latest `main`.
  - It covers exactly one component. Never create a branch that releases two components (e.g. write + presenter).
  - Its own commits only edit that component's CHANGELOG and version. All code reaches it through feature/fix branches merged into it.
- **Feature / fix branch**: a descriptive kebab-case name, hyphens not underscores (e.g. `data-retention`). Create it from the release branch it targets, and merge it back into that release branch, never into `main`.
  - Its changes stay inside the target component's modules. If work needs changes in two components (e.g. a `devin-write` provider change used by `present-app`), split it into one branch per component, each targeting its own release branch.

## Releasing

1. On the release branch, add a `## X.Y.Z` section at the top of the component's CHANGELOG with one bullet per user-visible change, and set the version (see the table).
   - `presenter`: also increment `versionCode` by 1 (it must be exactly the latest `presenter_*` tag's `versionCode` + 1). Android uses it to decide whether an APK is an upgrade. Then run `scripts/check-presenter-release.sh`; it checks the tag is new, the `versionCode` step, and the CHANGELOG section. `release-presenter.yml` runs the same script and refuses to release if it fails.
   - Libraries released as a pair (`devin-write` + `devin-no-op`, `devin-write-okhttp` + `devin-write-okhttp-no-op`) must have the same `POM_VERSION_NAME`.
2. Merge the release branch into `main` via a pull request.
3. Run the component's release workflow from the Actions tab, **on `main`**. It reads the version, refuses an existing tag (presenter: also checks `versionCode`), takes the release notes from the `## X.Y.Z` CHANGELOG section, publishes, and creates the tag and GitHub Release.
4. Merge `main` into every other open release branch, so they build on what was just released.

## Ordering between components

- A component that depends on another releases after it. `write-okhttp` depends on `write`, so the `write` version it uses must already be on Maven Central (the workflow checks this). Release `write` first, then merge `main` into the `write-okhttp` release branch.
- `present-app` builds `devin-write` from source (`project(":devin-write")`), so a presenter release ships whatever `devin-write` code is on `main`. A presenter feature that needs a `devin-write` change waits until that change has been released as `write` and merged into `main`, then merge `main` into the presenter release branch.

# Project Map

Devin: Android logging framework. Write-side library apps embed to produce logs, and a standalone presenter app that reads/displays/exports them.

| Module | Type | Purpose |
|---|---|---|
| `devin-write` | Android library | Core write module (Room DB, `DevinContentProvider`, logging APIs). Published to Maven Central as `io.github.nasserkhosravi.devin:write`. |
| `devin-no-op` | Android library | No-op stub of `devin-write`, same API, empty bodies. Used in release builds for zero overhead. |
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
