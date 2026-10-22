# FilterDialog Compose Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the XML/DialogFragment-based `FilterDialog` with a Jetpack Compose composable, the second screen in `present-app`'s gradual Compose migration (after `LogDetailDialog`).

**Architecture:** Rewrite `FilterDialog.kt` in place as a `@Composable` function taking `onDismiss`/`onConfirm` callbacks. Host it from `LogActivity` via a small `FilterDialogHost` class (same `ComposeView`-in-`init`-block pattern as the existing `LogDetailDialogHost`, kept consistent per explicit user decision). Delete `dialog_filter.xml`. Reuses the `DevinTheme`/`MaterialTheme.spacing` foundation already in place from the previous migration — no new theme work needed.

**Tech Stack:** Same as the previous migration — Kotlin 1.9.0, Compose compiler 1.5.2, Compose BOM 2023.09.02, Compose Material3. All already configured in this module; this plan touches zero build files.

**Testing note:** No unit tests added — pure UI, and per `AGENTS.md` tests/commits require explicit go-ahead. Each task ends with a compile check; behavior is verified manually per the spec's Testing section. Commit points are called out but not executed.

---

### Task 1: Rewrite `FilterDialog.kt` as a composable

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/FilterDialog.kt`
- Delete: `present-app/src/main/res/layout/dialog_filter.xml`

- [ ] **Step 1: Replace the entire file contents**

The current file is a `DialogFragment` (`BaseDialog` subclass) with `onConfirm` as a mutable field, `newInstance(lastIndex)`, and a `confirmedRequested()` validator. Replace it entirely with:

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

- [ ] **Step 2: Delete the old layout file**

```bash
rm present-app/src/main/res/layout/dialog_filter.xml
```

- [ ] **Step 3: Confirm nothing else references the deleted layout**

```bash
grep -rn "dialog_filter\|DialogFilterBinding" present-app/src/main
```

Expected: no output. If `DialogFilterBinding` shows up anywhere outside the file you just rewrote, stop — something else depends on it.

- [ ] **Step 4: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: **FAIL** — `LogActivity.kt`'s `createFilter()` still calls `FilterDialog.newInstance(...)` and sets `.onConfirm`/`.show(...)`, none of which exist anymore. This is expected; Task 3 fixes the call site. Confirm the failure is specifically unresolved references at that one call site (around `LogActivity.kt:507-515`), not something else.

---

### Task 2: Create `FilterDialogHost.kt`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/FilterDialogHost.kt`

- [ ] **Step 1: Write the file**

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

Note: this class is in the `present.present` package (same as `FilterDialog`), matching where `LogDetailDialogHost` itself already lives (`present-app/src/main/java/com/khosravi/devin/present/present/LogDetailDialogHost.kt`) — per the previous migration's review, one-off dialog hosts belong next to their dialog in `present/present/`, not the generic `uikit/` package.

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: still **FAIL** with the same single call-site error from Task 1 Step 4 — no new errors from `FilterDialogHost.kt` itself.

---

### Task 3: Wire the host into `LogActivity`

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt`

- [ ] **Step 1: Add the host field**

Near the existing `logDetailDialogHost` field (added by the previous migration), add:

```kotlin
private val filterDialogHost by lazy { FilterDialogHost(this, binding.root) }
```

(No import needed — `FilterDialogHost` is in the same package, `com.khosravi.devin.present.present`, as `LogActivity`.)

- [ ] **Step 2: Replace `createFilter()`**

Change:

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

to:

```kotlin
    private fun createFilter() {
        filterDialogHost.show { viewModel.addFilter(it, CALLBACK_ID_ADD_FILTER) }
    }
```

- [ ] **Step 3: Verify the full module builds**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit point** — full migration compiles end-to-end. Stop here; ask before committing.

---

### Task 4: Manual verification — `LogActivity`

**Files:** none (manual testing only, per spec)

- [ ] **Step 1: Install and launch**

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:installDebug
```

Open the app to `LogActivity`, tap the "create filter" entry point (the `+` / filter action that calls `createFilter()`).

- [ ] **Step 2: Validation — both fields blank**

Leave title and tag both empty, tap confirm. Confirm: dialog stays open, both fields show the error (red outline) state, no filter is added.

- [ ] **Step 3: Only tag filled**

Clear the error state (type then delete, or just fill tag), leave title blank, fill tag with a real tag from your logs, tap confirm. Confirm: dialog closes, a new filter chip appears using the tag as its title, selecting it filters the log list by that tag.

- [ ] **Step 4: Only title filled**

Reopen the dialog, fill only title (leave tag blank), tap confirm. Confirm: filter chip appears using the title, selecting it does not restrict by tag (shows all tags).

- [ ] **Step 5: Title + tag + search text**

Reopen, fill all three fields, tap confirm. Confirm: filter chip appears, selecting it filters by both the tag and the search text together.

- [ ] **Step 6: Dismiss without confirming**

Reopen the dialog, tap outside it (or press back). Confirm: dialog closes, no filter chip was added.

- [ ] **Step 7: Theme check**

Toggle the app's light/dark theme, reopen the dialog in each mode. Confirm text fields and button are legible in both.
