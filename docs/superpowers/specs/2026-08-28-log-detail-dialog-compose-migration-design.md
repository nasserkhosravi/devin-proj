# LogDetailDialog → Jetpack Compose Migration — Design

Date: 2026-08-28
Branch: `compose_4.4.0`

## Problem

`present-app` has zero Compose adoption today (XML + ViewBinding + FastAdapter throughout). The user wants to migrate gradually, starting with the smallest, lowest-risk screen: `LogDetailDialog` — a `DialogFragment` showing three read-only `TextView`s (tag, message, meta) for a `TextLogItemData`. This is the first Compose screen in the module, so it also introduces Compose tooling to the project.

Decision (user-confirmed): full rewrite, not an interop shim. `LogDetailDialog.kt` (DialogFragment) and `dialog_log_detail.xml` are deleted once the Compose replacement is wired up — no dead code left behind.

## Approach

### 1. Project setup

- Add to `gradle/libs.versions.toml`: Compose BOM version, and library aliases for `compose-bom`, `compose-ui`, `compose-ui-tooling-preview`, `compose-material3`, `activity-compose`.
- `present-app/build.gradle.kts`: add `buildFeatures { compose = true }`, `composeOptions { kotlinCompilerExtensionVersion = "1.5.3" }` (matches Kotlin 1.9.0 already in use), add the new dependencies (`platform(libs.compose.bom)`, `compose.ui`, `compose.ui.tooling.preview`, `compose.material3`, `activity.compose`), plus `debugImplementation(libs.compose.ui.tooling)` for layout inspector support.

### 2. UIKit foundation (new)

Fresh, independent-of-XML token set — not bridged from `colors.xml`/`dimens.xml`. This is the first Compose screen, so it also seeds the shared theme every future screen will reuse. New package `present-app/.../uikit/theme/`:

- **`Spacing.kt`** — token scale + `CompositionLocal`, no hardcoded `.dp` in screen code:
  ```kotlin
  data class Spacing(
      val xs: Dp = 4.dp,
      val small: Dp = 8.dp,
      val medium: Dp = 16.dp,
      val large: Dp = 24.dp,
      val xlarge: Dp = 32.dp,
  )
  val LocalSpacing = staticCompositionLocalOf { Spacing() }
  val MaterialTheme.spacing: Spacing
      @Composable @ReadOnlyComposable get() = LocalSpacing.current
  ```
- **`Color.kt`** — light/dark `ColorScheme` (Material3 `lightColorScheme()`/`darkColorScheme()` seeded with the app's own palette, independent from `colors.xml`).
- **`Theme.kt`** — `DevinTheme` composable, the single entry point every screen wraps itself in:
  ```kotlin
  @Composable
  fun DevinTheme(content: @Composable () -> Unit) {
      val colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
      CompositionLocalProvider(LocalSpacing provides Spacing()) {
          MaterialTheme(colorScheme = colorScheme, content = content)
      }
  }
  ```
  Note: `isSystemInDarkTheme()` correctly reflects the app's manual light/dark toggle too — `AppCompatDelegate.setDefaultNightMode()` updates `Configuration.uiMode` and recreates the activity, so no separate bridging to `UserSettings`/`AppPref` is needed.

Typography is intentionally left at Material3 defaults for now — no `Type.kt` yet (YAGNI; add only when a screen actually needs custom type styles).

### 3. Composable

New file `present-app/.../present/LogDetailDialog.kt` (same package/name, now a `@Composable` function instead of a class):

```kotlin
@Composable
fun LogDetailDialog(data: TextLogItemData, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(MaterialTheme.spacing.medium)
            ) {
                Text(data.tag, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(MaterialTheme.spacing.small))
                Text(data.text, style = MaterialTheme.typography.bodyMedium)
                data.meta?.let {
                    Spacer(Modifier.height(MaterialTheme.spacing.small))
                    Text(it.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
```

Matches current layout structure (scrollable column, tag/message/meta stacked). No hardcoded `.dp` or `Color(...)` literals — all spacing/color comes from `DevinTheme`/`MaterialTheme.spacing`. Caller wraps this in `DevinTheme { LogDetailDialog(...) }` (see below), not a bare `MaterialTheme { }`.

### 4. Hosting in caller Activities

`LogActivity` and `ImportLogActivity` are plain View-based `AppCompatActivity`s with no Compose host today. Each needs:

- One `ComposeView` added to the activity's view hierarchy (added programmatically to the existing root `ViewGroup`, `WRAP_CONTENT`/overlay — no changes to the rest of the XML layout).
- One `mutableStateOf<TextLogItemData?>(null)` field holding "currently shown detail data" (`null` = hidden).
- `composeView.setContent { DevinTheme { logDetailState.value?.let { LogDetailDialog(it) { logDetailState.value = null } } } }`.

Call sites change:
- `LogActivity.kt:518`: `LogDetailDialog.newInstance(item.data).show(supportFragmentManager, LogDetailDialog.TAG)` → `logDetailState.value = item.data`.
- `ImportLogActivity.kt:132-133`: same pattern.

### 5. Cleanup

Delete `dialog_log_detail.xml`. The old `LogDetailDialog` class is replaced in-place (same file, same name, now a composable) rather than deleted-and-recreated separately.

## Touch Points

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add Compose BOM + library aliases |
| `present-app/build.gradle.kts` | Enable Compose, add dependencies |
| `present-app/.../uikit/theme/Spacing.kt` | New — `Spacing` tokens + `CompositionLocal` + `MaterialTheme.spacing` accessor |
| `present-app/.../uikit/theme/Color.kt` | New — light/dark `ColorScheme` |
| `present-app/.../uikit/theme/Theme.kt` | New — `DevinTheme` composable |
| `present-app/.../present/LogDetailDialog.kt` | Rewrite: `DialogFragment` class → `@Composable` function |
| `present-app/.../res/layout/dialog_log_detail.xml` | Delete |
| `present-app/.../present/LogActivity.kt` | Add `ComposeView` + state field; replace `.show()` call at line 518 |
| `present-app/.../present/ImportLogActivity.kt` | Add `ComposeView` + state field; replace `.show()` call at lines 132-133 |

## Testing

- Manual only (UI change, no unit test coverage expected or required per project convention).
- Verify in both `LogActivity` and `ImportLogActivity`: tapping a log item opens the dialog with correct tag/message/meta, scrolls when content is long, dismisses on outside-tap and back button.
- Verify light/dark mode both render legibly, and that toggling the app's manual theme switch (not just system setting) is reflected in `DevinTheme`.

## Out of Scope

- Bridging XML theme (`colors.xml`, `dimens.xml`, `DialogTheme`) into `DevinTheme` — deliberately independent token set, not a port.
- Typography tokens (`Type.kt`) — deferred until a screen needs custom text styles beyond Material3 defaults.
- Migrating any other dialog/activity — this is step one of a gradual migration. Every future Compose screen reuses `DevinTheme` from `uikit/theme/`.
- FastAdapter → LazyColumn migration (separate, larger future step).
