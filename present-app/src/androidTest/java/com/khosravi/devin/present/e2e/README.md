# present-app e2e suite

Black-box instrumented tests for the presenter's Compose UI. Behaviour IDs (`START-03`, `ROW-04`, …) refer to
`docs/superpowers/specs/2026-10-20-presenter-behaviour-inventory.md`; each test's KDoc lists the IDs it covers.

## Run

Exactly one device: an emulator. Never a personal phone — the suite wipes the app's DB and prefs.

```sh
export ANDROID_SERIAL=emulator-5554
JAVA_HOME=<JDK 17> ./gradlew :present-app:connectedDebugAndroidTest
```

Equivalent without Gradle's test runner (what was used while Google Maven was unreachable):

```sh
./gradlew :present-app:assembleDebug :present-app:assembleDebugAndroidTest
adb install -r -t present-app/build/outputs/apk/debug/present-app-debug.apk
adb install -r -t present-app/build/outputs/apk/androidTest/debug/present-app-debug-androidTest.apk
adb shell am instrument -w -r --no-window-animation -e package com.khosravi.devin.present.e2e \
  com.khosravi.devin.present.test/com.khosravi.devin.present.e2e.E2eTestRunner
```

Give the emulator at least 3 GB RAM (`-memory 3072`); with 2 GB, API 36 System UI stalls and app windows stop
redrawing, which shows up as random timeouts.

## How it works

- **Driver**: `Ui` walks the platform accessibility tree (the tree UiAutomator reads; Compose exposes its
  semantics there) through `UiAutomation`: find by text / content description, click, long-click, set text,
  scroll, IME action. Only `androidx.test:runner` and `androidx.test.ext:junit` are needed.
- **State**: `AppState.reset()` (before every test) finishes all activities, clears the Room DB and every
  SharedPreferences file through the cached instances, cancels notifications and suppresses the GitHub update
  check (`E2eTestRunner` also does that before `Application.onCreate`). This stands in for the orchestrator's
  `clearPackageData`.
- **Data**: `TestData` writes through the app's own `DevinContentProvider` (clients, text/session/image logs,
  HTTP logs with a HAR meta blob like `devin-write-okhttp`). Dates are recent (inside the default retention) and
  end in `.123` ms.
- **System UI**: `IntentStubs` (an `Instrumentation.ActivityMonitor`, like Espresso-Intents) records every
  activity start and stubs choosers / `ACTION_VIEW`; save pickers answer with a `file://` Uri in the app cache so
  the written file can be checked. `Toasts` reads toasts from accessibility events; `Clipboard` reads the clip.
- **Update dialogs**: `AppState.seedLatestRelease()` writes the cached release prefs and pushes them into the
  process-wide `UpdateChecker`.
