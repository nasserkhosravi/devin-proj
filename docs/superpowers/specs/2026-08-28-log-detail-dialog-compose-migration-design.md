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

### 2. Composable

New file `present-app/.../present/LogDetailDialog.kt` (same package/name, now a `@Composable` function instead of a class):

```kotlin
@Composable
fun LogDetailDialog(data: TextLogItemData, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(data.tag, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(data.text, style = MaterialTheme.typography.bodyMedium)
                data.meta?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
```

Matches current layout structure (scrollable column, tag/message/meta stacked) using Material3 defaults. Not attempting to bridge the exact XML `DialogTheme`/`?attr/spaceSmall` values — wrapped in a plain `MaterialTheme` with default light/dark color scheme. Pixel-perfect theme parity is out of scope for this first screen (see Out of Scope).

### 3. Hosting in caller Activities

`LogActivity` and `ImportLogActivity` are plain View-based `AppCompatActivity`s with no Compose host today. Each needs:

- One `ComposeView` added to the activity's view hierarchy (added programmatically to the existing root `ViewGroup`, `WRAP_CONTENT`/overlay — no changes to the rest of the XML layout).
- One `mutableStateOf<TextLogItemData?>(null)` field holding "currently shown detail data" (`null` = hidden).
- `composeView.setContent { MaterialTheme { logDetailState.value?.let { LogDetailDialog(it) { logDetailState.value = null } } } }`.

Call sites change:
- `LogActivity.kt:518`: `LogDetailDialog.newInstance(item.data).show(supportFragmentManager, LogDetailDialog.TAG)` → `logDetailState.value = item.data`.
- `ImportLogActivity.kt:132-133`: same pattern.

### 4. Cleanup

Delete `dialog_log_detail.xml`. The old `LogDetailDialog` class is replaced in-place (same file, same name, now a composable) rather than deleted-and-recreated separately.

## Touch Points

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add Compose BOM + library aliases |
| `present-app/build.gradle.kts` | Enable Compose, add dependencies |
| `present-app/.../present/LogDetailDialog.kt` | Rewrite: `DialogFragment` class → `@Composable` function |
| `present-app/.../res/layout/dialog_log_detail.xml` | Delete |
| `present-app/.../present/LogActivity.kt` | Add `ComposeView` + state field; replace `.show()` call at line 518 |
| `present-app/.../present/ImportLogActivity.kt` | Add `ComposeView` + state field; replace `.show()` call at lines 132-133 |

## Testing

- Manual only (UI change, no unit test coverage expected or required per project convention).
- Verify in both `LogActivity` and `ImportLogActivity`: tapping a log item opens the dialog with correct tag/message/meta, scrolls when content is long, dismisses on outside-tap and back button.
- Verify light/dark mode both render legibly (default Material3 scheme vs. existing app theme — visual mismatch is expected and acceptable for this step).

## Out of Scope

- Bridging exact XML theme (`DialogTheme`, `?attr/spaceSmall`) into Compose — deferred until more screens migrate and a shared Compose theme is worth building.
- Migrating any other dialog/activity — this is step one of a gradual migration.
- FastAdapter → LazyColumn migration (separate, larger future step).
