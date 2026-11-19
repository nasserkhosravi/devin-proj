# Resizable Windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `present-app` targets SDK 36 and behaves well in any window size: on a wide window every screen keeps its phone layout, centered with a maximum width, and state survives resize, rotation and fold/unfold.

**Architecture:** One shared composable, `DevinWindow`, wraps the content of each activity's `setContent`: a full-size box with the page background, holding the screen in a centered column of at most `MAX_CONTENT_WIDTH`. Narrower windows (phones, narrow split-screen) are unaffected. The SDK bump is local to `present-app`. State survival is audited on a device first, then fixed where it is lost.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Gradle/AGP 8.10 (supports API 36).

**Spec:** GitHub issue nasserkhosravi/devin-proj#155 (milestone "presnter 4.11.0").

## Global Constraints

- Branching: feature branch `resizable-windows` was cut from `presenter-release-4.11.0` (after PRs #184–#187 merged). It merges back into it, never `main` (AGENTS.md).
- Commits and tests: per AGENTS.md, **don't commit, and don't add or change tests, until the user says so**. This plan adds no new unit tests (layout and manifest work; checked by build, the existing suite and by hand), so only the commit steps wait.
- Gradle needs JDK 17: prefix commands with `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`.
- **Do not touch** `gradle/libs.versions.toml`'s `project-compileSdk` / `project-targetSdk` (they stay 35): the library modules use Gradle plugin 8.3.2 and share them. `present-app` gets its own literal values.
- Maximum content width: **600dp** (a call made in the spec, not yet confirmed by the user). Dialogs and sheets are only checked, not changed, unless they stretch.
- Phones must look exactly as before.
- Changes stay inside `present-app`. Release bookkeeping (CHANGELOG, `versionName`/`versionCode`) is not part of this branch.

## Review Focus

1. **Android 16 predictive back** (default at target 36): back in log-list selection mode, with the tag search open, and with a dialog open must still behave as before (Task 1, by hand).
2. **Window narrower than 600dp** (phone, narrow split-screen) is unchanged, including system-bar and cut-out insets (Task 2, by hand).
3. **Soft keyboard in a wide window:** a focused text field (tag search, create-filter dialog, settings dialogs) stays visible (Task 2).
4. **Dialogs, the password sheet and bottom sheets** don't stretch across a wide window (Task 2).
5. **Rotation or resize mid-way:** scroll position, selected filter, expanded groups, open dialogs and typed text survive; anything lost is fixed or listed in the PR (Task 3).

---

### Task 1: Move `present-app` to SDK 36

**Files:**
- Modify: `present-app/build.gradle.kts` (`compileSdk`, `targetSdk`)
- Modify: `present-app/src/test/resources/robolectric.properties` (comment only)

**Interfaces:**
- Produces: `present-app` compiled and targeted at 36; everything else unchanged.

- [ ] **Step 1: Set the values.** In `present-app/build.gradle.kts` replace the two `libs.versions.project.*` reads with `compileSdk = 36` and `targetSdk = 36`, with a one-line comment that the libraries stay on the shared value. Keep `minSdk` from the catalog. Update the `robolectric.properties` comment to say the app targets 36 and Robolectric 4.12 still runs at `sdk=34` (leave `sdk=34`).
- [ ] **Step 2: Build and test.** `./gradlew :present-app:assembleDebug` and `:present-app:testDebugUnitTest` → BUILD SUCCESSFUL. If a dependency demands a newer plugin or `compileSdk`, report it in the PR rather than changing other modules. `./gradlew :devin-write:assembleDebug` still builds unchanged.
- [ ] **Step 3: Check Android 16 behavior changes.** Read the API 36 behavior-change notes and, for each that touches this app (predictive back is on by default, orientation/resizability opt-outs ignored on large screens, edge-to-edge is already enforced since 35), note the result in the commit message. The manifest sets no orientation, resizability or aspect ratio today, so nothing to remove.
- [ ] **Step 4: Check by hand** on the Samsung (Android 13 here, so the API 36 rules aren't enforced by the OS; this only proves the new build works): clients, log list, selection mode + back, tag search + back, create-filter dialog + back, Settings, HTTP detail, the password sheet. All behave as before.
- [ ] **Step 5: Commit** (after go-ahead): `Target SDK 36 for the presenter`

### Task 2: Centered content on wide windows

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/uikit/layout/DevinWindow.kt`
- Modify: the `setContent` of `StarterActivity`, `LogActivity`, `ImportLogActivity`, `SettingsActivity` and `HttpLogDetailActivity` (all under `present-app/src/main/java/com/khosravi/devin/present/present/`)

**Interfaces:**
- Produces: `val MAX_CONTENT_WIDTH: Dp = 600.dp` and `@Composable fun DevinWindow(content: @Composable () -> Unit)`: a `Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter)` containing `Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxHeight())` that holds `content`. Must be called inside `DevinTheme`.

- [ ] **Step 1: Write `DevinWindow`** with a short KDoc saying why (wide windows keep the phone layout, centered).
- [ ] **Step 2: Wrap each activity's content:** `DevinTheme { DevinWindow { <existing content> } }` in the five `setContent` blocks. The inner screens (`Scaffold`s) already handle insets themselves; don't add any.
- [ ] **Step 3: Build.** `./gradlew :present-app:assembleDebug` and the unit suite → BUILD SUCCESSFUL.
- [ ] **Step 4: Check by hand.** To get a wide window on the phone, lower its density with `adb shell wm density 240` (a 1080px-wide screen is then 720dp wide; width in dp = px ÷ (density ÷ 160)), then `adb shell wm density reset` at the end (and `wm size reset` if you also changed the size). On a wide window, every screen (clients, log list, import, HTTP detail, Settings) shows its content centered at most 600dp wide, with the page background on both sides. On the normal phone size nothing changed (compare with a screenshot from before). Focus the tag search and a dialog field: the field stays above the keyboard. The create-filter dialog, the password sheet and the Settings dialogs are not wider than the content column.
- [ ] **Step 5: Commit** (after go-ahead): `Center content on wide windows`

### Task 3: State survives resize and rotation

**Files:**
- Modify: only what the audit finds, most likely `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt` and `ReaderViewModel.kt`.

**Interfaces:**
- Consumes: Task 2's wide window for testing; none of its code.

- [ ] **Step 1: Audit.** On the device, with a log list that has several pages, a selected custom filter, an expanded repeated-log group, scroll position past the first page, tag search text typed and the create-filter dialog half filled in, rotate the phone (portrait ↔ landscape) and change the window width (`wm density` as in Task 2, and `wm size` for a different aspect). For each of: list scroll position, selected filter, expanded groups, search text, open dialog and its fields, selection mode, record kept or lost. Do the same for Settings and the HTTP detail (scroll position, open dialog). Put the table in the PR description.
- [ ] **Step 2: Fix what is lost,** smallest change first: `rememberSaveable` for UI-only state, the ViewModel (which survives recreation) for data. Only if the audit finds widespread loss, consider declaring `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density"` on `LogActivity` so it isn't recreated; if so say why in the PR, since resource changes would then need handling.
- [ ] **Step 3: Re-run the audit** and confirm every row is kept (or listed in the PR as a known gap with the reason).
- [ ] **Step 4: Build and unit suite** → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** (after go-ahead): `Keep presenter state across resize and rotation` (skip the commit if the audit found nothing to fix; then the audit table in the PR is the deliverable).
