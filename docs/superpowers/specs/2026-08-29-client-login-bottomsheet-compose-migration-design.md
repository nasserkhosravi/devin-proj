# ClientLoginBottomSheet → Jetpack Compose Migration — Design

Date: 2026-08-29
Branch: `compose-client-login` (based on `compose-starter-activity`, third in the gradual `present-app` Compose migration series)

## Problem

Fourth step of the migration. `ClientLoginBottomSheet` is a `BottomSheetDialogFragment` with a password field (with visibility toggle) and confirm button, used to gate access to a client's logs when `presenterConfig` specifies a password. Unlike the previous two dialogs, it's not triggered from an Activity's own click handler — it's triggered from `ClientLoginInteractor.onClientSelect(activity, clientData, onNext)`, a plain injected class. Today its only caller is `StarterActivity` (already Compose, 3 call sites), but it takes a generic `AppCompatActivity` and is written to work the same way from any Activity, XML-based or Compose — e.g. if `LogActivity` (still XML-based, not yet migrated) ever needed a re-auth path through it.

Decision (user-confirmed): full rewrite, same as the prior two. `ClientLoginBottomSheet.kt` becomes a `@Composable` function; `fragment_client_login.xml` and `BaseBottomDialogFragment.kt` (its only caller) are deleted.

## Approach

### 1. Host lifecycle (the key departure from the last two migrations)

`ClientLoginInteractor.onClientSelect` keeps its exact current signature — `activity: AppCompatActivity` in, nothing else changes about how callers invoke it. Internally, instead of `.show(activity.supportFragmentManager, TAG)`, it constructs a **transient** host:

```kotlin
ClientLoginDialogHost(activity).show(
    correctPassword = password,
    onCorrectPassword = { ... },
    onWrongPassword = { ... }
)
```

`ClientLoginDialogHost` is *not* a persistent `by lazy` Activity field like `LogDetailDialogHost`/`FilterDialogHost` — it's constructed fresh on every call and discarded after use. This fits the call shape here: the trigger point is a shared, non-Activity-scoped interactor, not a single Activity's own click handler, so there's no natural Activity field to hang a persistent host off without touching `LogActivity.kt` (not otherwise in scope this round) in addition to `StarterActivity.kt`.

Attachment point: `activity.findViewById<ViewGroup>(android.R.id.content)` — the standard root `FrameLayout` every Activity has under its decor view, regardless of whether the Activity's own content is Compose (`StarterActivity`) or XML (`LogActivity`). This is why the interactor's signature doesn't need to change or know which kind of Activity it's talking to.

### 2. Composable

New file `present-app/src/main/java/com/khosravi/devin/present/present/ClientLoginBottomSheet.kt`:

```kotlin
package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.khosravi.devin.present.uikit.theme.spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientLoginSheet(
    correctPassword: String,
    onCorrectPassword: (String) -> Unit,
    onWrongPassword: () -> Boolean,
    onDismissed: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissed() }
    }

    fun submit() {
        if (isSubmitting) return
        isSubmitting = true
        if (password == correctPassword) {
            onCorrectPassword(password)
            close()
        } else {
            val forceClose = onWrongPassword()
            if (forceClose) {
                close()
            } else {
                errorText = "Incorrect password. Please try again."
                isSubmitting = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { close() }, sheetState = sheetState) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
            Text("Enter Numeric Password", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorText = null },
                label = { Text("Password") },
                singleLine = true,
                enabled = !isSubmitting,
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "HIDE" else "SHOW")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.large)
            )
            Button(
                onClick = { submit() },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.large)
            ) {
                Text("Confirm")
            }
        }
    }
}
```

Notes:
- **Re-entrancy guard (added after code review):** the first draft had no guard against a fast double-tap on Confirm firing `submit()` twice before the sheet's hide animation completed — traced end-to-end, this could stack two `LogActivity` instances from one correct-password double-tap (`LogActivity` has no special `launchMode`), or double-count a wrong attempt. `isSubmitting` blocks re-entry and disables the field/button while a submission is in flight; it resets to `false` only on the "stay open, show error" path, not on any path that leads to `close()`.
- **`close()`'s dismiss pattern (hardened after code review):** originally `scope.launch { sheetState.hide(); onDismissed() }`. Material3 1.1.2's `SheetState.hide()` is backed by a mutex that cancels an in-flight call if a second one starts before the first finishes — meaning a second `close()` call could silently skip the first's `onDismissed()`. It happened to work by incidental implementation detail, not contract. Switched to `scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissed() }`, the pattern Google's own docs recommend for this exact case, which guarantees `onDismissed()` still runs even if the `hide()` call is cancelled.
- `ModalBottomSheet`/`rememberModalBottomSheetState` are present in the project's pinned Material3 1.1.2 but marked `@ExperimentalMaterial3Api` — same situation as `TopAppBar` in `StarterScreen.kt`, same fix (`@OptIn`). Verify against the actual compiler during implementation, same as that task did.
- `supportingText` (Material3's built-in inline-error-message slot) is used here instead of the "no message, color-only" simplification from `FilterDialog` — this dialog's old error message was a real, meaningful piece of feedback ("Incorrect password..."), not just an empty-field marker, so it's worth the small extra API surface to keep it.
- Password visibility toggle is real functional parity (user-confirmed), not cosmetic — `showPassword` state + `VisualTransformation` swap + trailing `TextButton`.
- Checked (via the actual `material-icons-core-1.1.2.aar` classes, not assumption): `Icons.Default.Visibility`/`VisibilityOff` are NOT in `material-icons-core` — core only ships ~246 essential icons (e.g. `Refresh`, `Lock`); the eye glyphs live in the much larger `material-icons-extended` pack. Rather than add that dependency for one icon, the toggle uses a plain `TextButton` reading `"SHOW"`/`"HIDE"` in the trailing slot instead of an icon (user-confirmed) — zero new dependencies, same functional toggle.

### 3. Host class

New file `present-app/src/main/java/com/khosravi/devin/present/present/ClientLoginDialogHost.kt`:

```kotlin
package com.khosravi.devin.present.present

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.khosravi.devin.present.uikit.theme.DevinTheme

class ClientLoginDialogHost(private val activity: AppCompatActivity) {

    fun show(
        correctPassword: String,
        onCorrectPassword: (String) -> Unit,
        onWrongPassword: () -> Boolean,
    ) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        lateinit var composeView: ComposeView
        composeView = ComposeView(activity).apply {
            setContent {
                DevinTheme {
                    ClientLoginSheet(
                        correctPassword = correctPassword,
                        onCorrectPassword = onCorrectPassword,
                        onWrongPassword = onWrongPassword,
                        onDismissed = { root.removeView(composeView) }
                    )
                }
            }
        }
        root.addView(composeView)
    }
}
```

### 4. `ClientLoginInteractor` changes

`onClientSelect` — replace the `ClientLoginBottomSheet.newInstance(password).also { ... }.show(activity.supportFragmentManager, TAG)` block with:

```kotlin
ClientLoginDialogHost(activity).show(
    correctPassword = password,
    onCorrectPassword = { enteredPassword ->
        appPref.apply {
            resetLastWrongPasswordCount(clientData.id)
            saveConfirmedPassword(clientData.id, enteredPassword)
        }
        onNext(true)
    },
    onWrongPassword = {
        val wrongCount = appPref.increaseLastWrongPasswordCount(clientData.id)
        if (wrongCount == VALUE_MAX_WRONG_PASSWORD_TRY) {
            onNext(false)
            true // tell the sheet to force-close
        } else {
            false // stay open, just show the inline error
        }
    }
)
```

Behavioral note: old code called `onNext(false)` from inside the dialog's `onDismissListener` (i.e., *after* the visual dismiss completed). New code calls `onNext(false)` immediately when `wrongCount` hits max, then returns `true` so the sheet animates itself closed afterward. The net user-visible effect is the same (dialog closes, then `onNext(false)` behavior — e.g. `showManyTryPasswordToast` — takes over), just with `onNext(false)` firing slightly before the collapse animation finishes rather than slightly after. Not expected to be visually distinguishable; flagged here for completeness rather than as a design question, since re-ordering to match exactly would require threading a completion callback through `close()` for no real user-facing benefit.

### 5. Cleanup

Delete `present-app/src/main/res/layout/fragment_client_login.xml` and `present-app/src/main/java/com/khosravi/devin/present/arch/BaseBottomDialogFragment.kt` (confirmed single-caller — `ClientLoginBottomSheet` — via grep before deleting). `BaseDialogCommon.kt` is left alone — confirmed still used by `BaseDialog.kt`, whose only remaining subclass is `LogExportDialog` (not yet migrated), so it's not orphaned.

## Touch Points

| File | Change |
|---|---|
| `present-app/.../present/ClientLoginBottomSheet.kt` | Rewrite: `BottomSheetDialogFragment` class → `@Composable fun ClientLoginSheet(...)` |
| `present-app/.../present/ClientLoginDialogHost.kt` | New — transient host, constructed per-call |
| `present-app/.../domain/ClientLoginInteractor.kt` | Replace `.show(supportFragmentManager, TAG)` block with `ClientLoginDialogHost(activity).show(...)` |
| `present-app/.../res/layout/fragment_client_login.xml` | Delete |
| `present-app/.../arch/BaseBottomDialogFragment.kt` | Delete |

No changes needed: `StarterActivity.kt`, `LogActivity.kt`, `AppPref`, `ClientData`/`getLogPassword()` — all untouched, this migration is fully contained to the dialog + its interactor + its host.

## Testing

- Manual only, same convention as prior migrations.
- Confirmed test client already exists: `com.khosravi.sample.devin` (from `sample-app`'s `SampleActivity.kt`) sets `presenterConfig = DevinTool.PresenterConfigBuilder().logPassword("12346")...` — use password `12346` for the correct-password path, anything else for the wrong-password path.
- Verify: wrong password shows inline error, field stays open, can retry.
- Verify: password visibility toggle actually shows/hides the typed characters.
- Verify: correct password closes the sheet and proceeds to `LogActivity` via `StarterActivity`'s client list (the only current caller of `onClientSelect`).
- Verify: 4 wrong attempts in a row closes the sheet and shows the "Too many wrong password try" toast, without a 5th prompt. Note (from code review): `onNext(false)` — and therefore the toast — fires immediately when the 4th wrong attempt is detected, *before* the sheet's ~200-300ms close animation starts, not after. Expect the toast to visibly appear on top of the still-open, still-sliding-away sheet rather than after it's gone. This is a known, accepted cosmetic trade-off (see `ClientLoginInteractor` touch point above), not a bug to file — just don't be surprised by the overlap when you see it.
- Verify: swipe-down/tap-outside dismiss (without submitting) leaves the client unselected, no crash.
- Verify light/dark theme legibility.

## Out of Scope

- Exact `BottomSheetDialogTheme` styling (corner radius, drag handle appearance) — Material3 `ModalBottomSheet` defaults instead.
- Any other dialog/activity migration (next steps after this one).
