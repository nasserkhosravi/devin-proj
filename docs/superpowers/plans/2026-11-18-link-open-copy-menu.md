# Link Open/Copy Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tapping a highlighted link in the log detail screen shows a small popup with **Open** and **Copy** instead of opening the link right away.

**Architecture:** All of it lives in `LinkText` (`uikit/text/LinkText.kt`). `ClickableText` already reports the tapped character offset; the popup is a Material 3 `DropdownMenu` anchored at that character's bounding box (from `onTextLayout`). Hit-testing and the "needs a confirmation toast" rule become two small pure functions so they can be unit tested.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3, BOM 2023.09), JUnit 4.

**Spec:** GitHub issue nasserkhosravi/devin-proj#180 (milestone "presnter 4.11.0").

## Global Constraints

- Branching: feature branch `link-open-copy-menu` was cut from `group-repeated-logs` (stacked on PR #184, which edits `LogScreen.kt`/`LogActivity.kt`; this plan touches neither). It merges into `presenter-release-4.11.0`, never `main` (AGENTS.md).
- Commits and tests: per AGENTS.md, **don't commit, and don't add or change tests, until the user says so**. Test and commit steps run only after that go-ahead.
- Gradle needs JDK 17: prefix with `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`.
- Scope: `LinkText` with `clickable = true` (today only `LogItemDetailScreen`). `clickable = false` stays non-interactive. Long-press is untouched (it still starts text selection).
- Changes stay inside `present-app`.

## Ruling for the reader (differs from the issue's wording)

The issue says a "Link copied" **snackbar**. The app has no `SnackbarHost` anywhere and every copy confirmation is a `Toast` (`LogItemDetailCopy.kt`, `HttpLogDetailActivity.kt`). This plan uses a `Toast` with a new string "Link copied" to match, and skips it on Android 13+ (API 33), where the system already confirms a copy. Say so if a real snackbar is wanted.

## Review Focus

1. **Tap on text that isn't a link** opens no menu and does nothing (Task 1: `linkAt` returns null off a link).
2. **Tap just past a link's last character** must not hit the link (Task 1: range end is exclusive).
3. **`clickable = false`** (list rows' linkified text is a plain `Text` today, but the parameter exists) shows no menu (Task 2, by hand).
4. **No app handles the link**: Open shows the existing `link_no_handler` toast; the menu still closes (Task 2, by hand).
5. **Clipboard write fails** (`setClipboardSafe` returns false): no "Link copied" toast (Task 2).

---

### Task 1: Pure helpers

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/uikit/text/LinkText.kt`
- Test: `present-app/src/test/java/com/khosravi/devin/present/uikit/text/LinkTextTest.kt` (new)

**Interfaces:**
- Produces: `internal fun AnnotatedString.linkAt(offset: Int): String?` — the target uri of the link annotation (tag `LINK_TAG`) covering `offset`, else `null`.
- Produces: `internal fun needsCopyConfirmation(sdkInt: Int): Boolean` — `true` below `Build.VERSION_CODES.TIRAMISU` (33).

- [ ] **Step 1: Failing tests** (after go-ahead), using `linkifiedString("see https://a.io/x now", Color.Blue)`:
  - `linkAt returns the uri inside a link`: offsets `4` and `18` (first and last character of the link) → `"https://a.io/x"`.
  - `linkAt returns null off a link`: offsets `0` and `3`.
  - `linkAt treats the range end as outside`: offset `19` (the space after the link) → `null`.
  - `www links resolve to https`: `linkifiedString("www.a.io", Color.Blue).linkAt(0)` → `"https://www.a.io"`.
  - `needsCopyConfirmation is false from Android 13`: `32 → true`, `33 → false`, `34 → false`.
- [ ] **Step 2: Run, expect FAIL** (unresolved reference): `./gradlew :present-app:testDebugUnitTest --tests "*LinkTextTest"`
- [ ] **Step 3: Implement both.** `linkAt` is `getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.item` (the existing tap code, extracted). Verify with the tests that the end is exclusive; if `getStringAnnotations(tag, offset, offset)` includes the end, use `start <= offset && offset < end` over `getStringAnnotations(LINK_TAG, 0, length)` instead.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** (after go-ahead): `Add link hit-test and copy-confirmation helpers`

### Task 2: Popup menu in LinkText

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/uikit/text/LinkText.kt`
- Modify: `present-app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Task 1's `linkAt` and `needsCopyConfirmation`; existing `openLink(context, uri)` and `Context.setClipboardSafe(text): Boolean` (`Ext.kt`).
- Produces: strings `link_menu_open` ("Open"), `link_menu_copy` ("Copy"), `link_copied` ("Link copied"). `LinkText`'s signature does not change.

- [ ] **Step 1: Add the three strings.**
- [ ] **Step 2: Rewrite `LinkText`'s body.** Wrap `ClickableText` in a `Box(modifier)`. Keep `var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }` (set from `onTextLayout`) and `var menu by remember { mutableStateOf<LinkMenu?>(null) }`, where `private data class LinkMenu(val uri: String, val anchor: DpOffset)` — no new file. In `onClick`, when `clickable` and `annotated.linkAt(offset)` is non-null, set `menu` to that uri with the anchor at the bottom-left of `textLayout.getBoundingBox(offset)` converted with `LocalDensity`. Replace the direct `openLink` call; nothing opens until a menu item is chosen.
- [ ] **Step 3: Render the menu.** `DropdownMenu(expanded = menu != null, onDismissRequest = { menu = null }, offset = menu?.anchor ?: DpOffset.Zero)` with two `DropdownMenuItem`s: **Open** calls `openLink(context, uri)`; **Copy** calls `context.setClipboardSafe(uri)` and, only if that returned true and `needsCopyConfirmation(Build.VERSION.SDK_INT)`, shows `Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT)`. Both set `menu = null` first.
- [ ] **Step 4: Build.** `./gradlew :present-app:assembleDebug` → BUILD SUCCESSFUL. Then run the whole unit suite: `./gradlew :present-app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [ ] **Step 5: Check by hand** (connected device; `sample-app` "send log" with message `see https://example.com and myapp://x`):
  - Open the log's detail, tap the https link: popup appears under it, nothing opens. **Open** opens the browser. **Copy** puts the link in the clipboard (paste it somewhere); a "Link copied" toast shows on Android 12 and lower, none on 13+.
  - Tap the `myapp://x` link and choose Open: "No app can open this link" toast, menu closed.
  - Tap outside the menu or press back: it closes, nothing else happens. Tap plain text: no menu.
  - Long-press text near a link: selection still starts.
  - Rotate with the menu open: it closes without a crash.
- [ ] **Step 6: Commit** (after go-ahead): `Show an Open/Copy menu when tapping a link in a log`
