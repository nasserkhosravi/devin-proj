# FilterDialog → Jetpack Compose Migration — Design

Date: 2026-08-28
Branch: `compose-filter-dialog` (based on `compose-log-detail-dialog`, which added the `uikit/theme` foundation and the first Compose screen, `LogDetailDialog`)

## Problem

Second step of the gradual `present-app` Compose migration. `FilterDialog` is a `DialogFragment` with three text inputs (title, tag, search text) and a confirm button that builds a `CustomFilterItem` and invokes an `onConfirm` callback. Single call site: `LogActivity.createFilter()`.

Decision (user-confirmed): full rewrite, same pattern as `LogDetailDialog` — `FilterDialog.kt` becomes a `@Composable` function in place, `dialog_filter.xml` deleted, a small host class (`FilterDialogHost`) bridges it into `LogActivity`.

`FilterDialog.newInstance(lastIndex: Int)` currently stores `lastIndex` in a `Bundle` but never reads it anywhere in the class — dead code. User confirmed: drop it entirely in the Compose version.

## Approach

### 1. Composable

Replace `present-app/src/main/java/com/khosravi/devin/present/present/FilterDialog.kt` (currently a `DialogFragment` class) with:

```kotlin
package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.khosravi.devin.present.R
import com.khosravi.devin.present.filter.CustomFilterCriteria
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.itsNotEmpty
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun FilterDialog(onDismiss: () -> Unit, onConfirm: (CustomFilterItem) -> Unit) {
    var title by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; showError = false },
                    label = { Text(stringResource(R.string.field_title)) },
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MaterialTheme.spacing.small))
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it; showError = false },
                    label = { Text(stringResource(R.string.field_filter_tag)) },
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MaterialTheme.spacing.small))
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(R.string.field_search_text)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MaterialTheme.spacing.small))
                Button(
                    onClick = {
                        if (title.isBlank() && tag.isBlank()) {
                            showError = true
                            return@Button
                        }
                        val fTitle = title.ifBlank { tag }
                        onConfirm(
                            CustomFilterItem(
                                ui = FilterUiData(fTitle, fTitle.itsNotEmpty(), false),
                                criteria = CustomFilterCriteria(tag.ifBlank { null }, searchText)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}
```

Behavior parity with the old dialog:
- Validation only fires on confirm-tap (not live), matching the old `confirmedRequested()`.
- Both title and tag fields show an error state when both are blank (old code set `.error` on both `TextInputEditText`s) — `showError` drives both, clears the moment either field changes.
- Title falls back to tag when blank (`fTitle = title.ifBlank { tag }`, matches `if (title.isNullOrEmpty()) tag!! else title`).
- Empty tag becomes `null` in `CustomFilterCriteria` (`tag.ifBlank { null }`) — old code passed the raw (possibly empty) string; this is a minor, harmless tightening since `CustomFilterCriteria.tag` is already nullable and downstream filtering treats empty/null tag equivalently (no tag filter applied). Flagged here for visibility, not asking for separate sign-off since behavior is equivalent.

Not carried over: old error message string (`msg_title_required`) was set as inline `EditText` error text via `setError()`. Material3 `OutlinedTextField.isError` only toggles the error color state, no built-in inline message unless a `supportingText` is added. Since this is a minor visual difference (red-outlined empty fields vs red-outlined-with-message), and adding `supportingText` would add scope beyond a direct parity port, the composable omits the message for now — same class of accepted, deliberate simplification as `LogDetailDialog`'s dropped `dialogMinWidth`/exact `DialogTheme` styling.

Third deliberate simplification (flagged in code review, documented here): the old validation checked `title.isNullOrEmpty() && tag.isNullOrEmpty()`, so a whitespace-only title (e.g. `" "`) counted as "filled" and was accepted, producing a filter with a blank-looking title. The Compose version checks `title.isBlank() && tag.isBlank()`, rejecting whitespace-only input as empty. This is a stricter, arguably-more-correct behavior change, not a bug — accepted as-is rather than reverted to the old looser check.

### 2. Host

New file `present-app/src/main/java/com/khosravi/devin/present/present/FilterDialogHost.kt` (same package as `FilterDialog`, same location pattern as `LogDetailDialogHost` — lesson learned from the last migration's review: keep one-off dialog hosts in `present/present/`, not the generic `uikit/` package):

```kotlin
package com.khosravi.devin.present.present

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.uikit.theme.DevinTheme

class FilterDialogHost(context: Context, root: ViewGroup) {

    private var visible by mutableStateOf(false)
    private var onConfirm: ((CustomFilterItem) -> Unit)? = null

    init {
        val composeView = ComposeView(context).apply {
            setContent {
                DevinTheme {
                    if (visible) {
                        FilterDialog(
                            onDismiss = { visible = false },
                            onConfirm = { item ->
                                onConfirm?.invoke(item)
                                visible = false
                            }
                        )
                    }
                }
            }
        }
        root.addView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    fun show(onConfirm: (CustomFilterItem) -> Unit) {
        this.onConfirm = onConfirm
        visible = true
    }
}
```

Unlike `LogDetailDialogHost` (which carries per-call data, `TextLogItemData`), this dialog has no per-call input data — only a per-call `onConfirm` callback, since the old `onConfirm` field was set fresh on every `newInstance()` call at the single call site. Storing it as a plain field (not passed through `show()`'s composition) mirrors the old mutable-field-on-the-fragment pattern, adapted to the host.

### 3. Call site

`LogActivity.kt` — add field (same pattern as `logDetailDialogHost`):
```kotlin
private val filterDialogHost by lazy { FilterDialogHost(this, binding.root) }
```

Replace `createFilter()` (currently ~lines 507-515):
```kotlin
private fun createFilter() {
    FilterDialog.newInstance(filterItemAdapter.lastIndex()).apply {
        onConfirm = {
            viewModel.addFilter(it, CALLBACK_ID_ADD_FILTER)
            dismiss()
        }
        show(supportFragmentManager, FilterDialog.TAG)
    }
}
```
with:
```kotlin
private fun createFilter() {
    filterDialogHost.show { viewModel.addFilter(it, CALLBACK_ID_ADD_FILTER) }
}
```
No explicit dismiss call needed — the host hides itself right after invoking `onConfirm` (see `FilterDialogHost` above). `filterItemAdapter.lastIndex()` is no longer computed/passed, since it was never used by the old dialog either.

### 4. Cleanup

Delete `present-app/src/main/res/layout/dialog_filter.xml`. Old `FilterDialog` class (extends `BaseDialog`) replaced in-place, same as `LogDetailDialog`'s migration. `R.string.field_title`, `field_filter_tag`, `field_search_text`, `confirm` stay in use (via `stringResource`). `R.string.msg_title_required` becomes unused (no `supportingText` in this version) — left in `strings.xml` rather than deleted, since removing unused string resources is out of scope for this migration.

## Touch Points

| File | Change |
|---|---|
| `present-app/.../present/FilterDialog.kt` | Rewrite: `DialogFragment` class → `@Composable` function |
| `present-app/.../present/FilterDialogHost.kt` | New — visibility + callback host |
| `present-app/.../res/layout/dialog_filter.xml` | Delete |
| `present-app/.../present/LogActivity.kt` | Add `filterDialogHost` field; replace `createFilter()` body |

No changes needed: `gradle/libs.versions.toml`, `present-app/build.gradle.kts`, `uikit/theme/*` — Compose tooling and the theme foundation already exist from the previous migration.

## Testing

- Manual only, same convention as the previous migration.
- Verify from `LogActivity`: open filter dialog (existing "create filter" entry point), confirm with only title filled, only tag filled, both blank (expect error state on both fields, dialog stays open), both filled — confirm the resulting filter chip appears with expected criteria.
- Verify dismiss on outside-tap/back leaves no filter added.
- Verify light/dark theme legibility (same as before).

## Out of Scope

- `supportingText`/inline error message parity — accepted simplification, `isError` color state only.
- Any other dialog/activity migration (next steps after this one).
