# present-app Compose migration — final cleanup — design

Date: 2026-10-16
Branch: `compose-migration` (last commit of the migration)

## Context

After LogExportDialog, Settings, ImportLog and HttpLogDetail moved to Compose, `present-app` has **no XML
layouts left**. Remaining View-era pieces:

- `tool/adapter/` FastAdapter helpers (`FastBindingItem`, `SelectableBindingItem`,
  `AbstractExpandableBindingItem`, `FastAdapterExt`) — no screen uses them.
- Dependencies `fastadapter`, `fastadapter-binding`, `fastadapter-expandable`, `android-spantastic`
  (only `present-app` declares them; spantastic has no code users).
- `buildFeatures.viewBinding = true` with nothing to bind.
- `StarterActivity` still shows the force-update and optional-update prompts with
  `androidx.appcompat.app.AlertDialog` (ported unchanged during the 4.7.0 merge; planned to move to Compose).
- Orphaned resources: unused `vh_item_*` ids in `res/values/ids.xml`, `AppTheme.TextAppearance.Label/Value/Body`
  styles, drawables/colors/dimens only the deleted layouts used (lint UnusedResources list), possibly
  `SearchHighlightUtil.highlightWithDefinedColors*` (now only used by `SearchHighlightUtilTest` — KEEP: tests
  reference them and tests may not be changed).

## Decisions

1. **Delete `tool/adapter/*`** and remove the four dependencies from `present-app/build.gradle.kts`
   and their entries from `gradle/libs.versions.toml` (plus the unused version refs).
2. **`viewBinding = false`** (remove the line).
3. **Update prompts → Compose `AlertDialog`** in `StarterScreen`, driven by Activity state, keeping the
   exact behaviour:
   - Force update: title `updateTitle(update)`, message `updateMessage(update)`, single
     `update_action_download` button → `openReleasePage(update.pageUrl)`; not dismissable by outside tap;
     back (dismiss request) **finishes the Activity**; shown again on every resume while a force update is
     pending (it's state-driven now: visible whenever `availableUpdate?.isForceUpdate == true`); routing to
     logs stays blocked (`isBlockedByForceUpdate()` keeps working — it currently calls
     `showForceUpdateDialogIfNeeded`, which becomes a no-op/state update).
   - Optional update: shown when `update != null && !isForceUpdate && updateChecker.pendingPrompt() != null`
     and not already answered in this Activity instance; `update_action_download` (markPrompted + open
     page) and `update_action_later` (markPrompted) buttons; outside tap/back = markPrompted; on any close,
     run the held-back `pendingRoute` (routeAfterUpdatePrompt semantics unchanged). Read `UpdateChecker`
     (pendingPrompt/markPrompted) to keep the "up to 3 times a day" logic exact. Use `rememberSaveable`/
     Activity state so a rotation doesn't re-show an answered prompt or lose an open one.
   - Delete the `AlertDialog` fields and their `onDestroy` dismissal; keep `onDestroy` clearing `pendingRoute`.
4. **Remove orphaned resources** reported by `./gradlew :present-app:lintDebug` as `UnusedResources`
   (ids, styles, drawables, colors, dimens). **Do not** remove strings/plurals in this step unless trivially
   provably unused and not part of `values-night` overrides; keep `R.style.DialogTheme` and app themes;
   keep anything referenced from the manifest, themes, or `res/xml`. Lint's NewApi errors in
   `HttpLogDetailData.kt` / `LatestLogNotificationObserver.kt` are pre-existing — leave them.
5. Keep `appcompat` and `material` (MDC) dependencies: the XML app themes (`Theme.Material3…`) and
   `AppCompatActivity`/`AppCompatDelegate` night mode still need them.

## Verification

`assembleDebug`, `testDebugUnitTest` (all pass), `lintDebug` shows no new errors and no remaining
UnusedResources for the removed categories. On device: starter screen with an optional update pending
(if testable: the UpdateChecker hits GitHub — if not testable, build-verified), force-update path
build-verified; every screen opens.
