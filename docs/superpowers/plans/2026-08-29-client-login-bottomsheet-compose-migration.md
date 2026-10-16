# ClientLoginBottomSheet Compose Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `BottomSheetDialogFragment`-based `ClientLoginBottomSheet` with a Jetpack Compose `ModalBottomSheet`, the fourth screen in `present-app`'s gradual Compose migration.

**Architecture:** Rewrite `ClientLoginBottomSheet.kt` in place as `@Composable fun ClientLoginSheet(...)`. Unlike the prior two dialogs (triggered from a single Activity's own click handler), this one is triggered from `ClientLoginInteractor`, a plain injected class shared by `StarterActivity` and `LogActivity` — so instead of a persistent `by lazy` Activity field, a new `ClientLoginDialogHost` is constructed fresh per call and attaches its `ComposeView` to `android.R.id.content` (present on every Activity regardless of Compose/XML content). `ClientLoginInteractor.onClientSelect`'s public signature is unchanged.

**Tech Stack:** Same Compose stack as the previous three migrations (Kotlin 1.9.0, Compose compiler 1.5.2, Compose BOM 2023.09.02, Material3 1.1.2). No new dependencies — `material-icons-core` (already present) turned out not to have the icons needed for the password toggle, so the design uses a plain `TextButton` instead (see Task 1).

**Testing note:** No unit tests added — pure UI, per `AGENTS.md` tests/commits require explicit go-ahead. Each task ends with a compile check; behavior is verified manually per the spec's Testing section. Commit points are called out but not executed.

---

### Task 1: Rewrite `ClientLoginBottomSheet.kt` as a composable

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/ClientLoginBottomSheet.kt`
- Delete: `present-app/src/main/res/layout/fragment_client_login.xml`

- [ ] **Step 1: Replace the entire file contents**

The current file is a `BaseBottomDialogFragment` (`BottomSheetDialogFragment`) subclass with a `PasswordInputListener` interface, `newInstance(correctPassword)`, and inline password-matching logic. Replace it entirely with:

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

Note: `Icons.Default.Visibility`/`VisibilityOff` are NOT in `material-icons-core` (verified against the actual `.aar` — core only has ~246 essential icons). Using a plain `TextButton` reading `"SHOW"`/`"HIDE"` instead avoids pulling in the much larger `material-icons-extended` pack for one icon — this is deliberate, not a placeholder to fix later.

Note (found in code review, already applied above): the first draft of `submit()` had no re-entrancy guard — a fast double-tap on Confirm before `close()`'s hide-animation finished could fire `onCorrectPassword`/`onWrongPassword` twice, which downstream stacks two `LogActivity` instances on a double-correct-password tap (traced through `ClientLoginInteractor` → `StarterActivity.isRouteSuccessful` → `startActivity`, `LogActivity` has no special `launchMode`). Fixed with the `isSubmitting` guard (also disabling the field/button while true). Separately, `close()`'s original `scope.launch { sheetState.hide(); onDismissed() }` relied on an incidental mutex-cancellation detail in Material3 1.1.2's `SheetState` to avoid a dropped `onDismissed()` if called twice — switched to the officially-recommended `scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissed() }`, which guarantees teardown runs even on cancellation.

- [ ] **Step 2: Delete the old layout file**

```bash
rm present-app/src/main/res/layout/fragment_client_login.xml
```

- [ ] **Step 3: Confirm nothing else references the deleted layout**

```bash
grep -rn "fragment_client_login\|FragmentClientLoginBinding" present-app/src/main
```

Expected: no output.

- [ ] **Step 4: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

**IMPORTANT — expected result is a MIX of two kinds of failure:**
1. `ClientLoginInteractor.kt` still calls the old removed `ClientLoginBottomSheet.newInstance(...)`/`.passwordInputListener =`/`.show(...)` API — this is expected, fixed in Task 3.
2. `ModalBottomSheet`/`rememberModalBottomSheetState` may report an `@ExperimentalMaterial3Api` opt-in error in THIS new file, similar to what happened with `TopAppBar` in the `StarterActivity` migration (that project's Material3 version, 1.1.2, marks several APIs experimental). If you see that specific error, it's expected too — the `@OptIn(ExperimentalMaterial3Api::class)` annotation is already in the code above and should suppress it. If it does NOT suppress it (e.g. a different/stricter error appears), diagnose against the real compiler output — don't guess — and report exactly what you find.

Confirm every error is one of these two expected kinds, nothing else. Paste the exact error lines.

---

### Task 2: Create `ClientLoginDialogHost.kt`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/ClientLoginDialogHost.kt`

- [ ] **Step 1: Write the file**

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

Note on the pattern: unlike `LogDetailDialogHost`/`FilterDialogHost` (persistent `by lazy` fields on one Activity, reused across many taps), `ClientLoginDialogHost` is constructed fresh every time `ClientLoginInteractor.onClientSelect` needs it (Task 3) and discarded after use — there's no field to add anywhere. This is intentional, not an oversight.

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: still fails, but ONLY with the `ClientLoginInteractor.kt` old-API error from Task 1 (the `ExperimentalMaterial3Api` question from Task 1 should already be resolved by that task's `@OptIn`). No new errors from `ClientLoginDialogHost.kt` itself.

---

### Task 3: Wire the host into `ClientLoginInteractor`, delete `BaseBottomDialogFragment`

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/domain/ClientLoginInteractor.kt`
- Delete: `present-app/src/main/java/com/khosravi/devin/present/arch/BaseBottomDialogFragment.kt`

- [ ] **Step 1: Replace the dialog-showing block in `onClientSelect`**

Find:

```kotlin
    fun onClientSelect(activity: AppCompatActivity, clientData: ClientData, onNext: (Boolean) -> Unit) {
        handleOpeningNextActivityRequest(clientData, { password ->
            ClientLoginBottomSheet.newInstance(password).also {
                it.passwordInputListener = object : PasswordInputListener {
                    override fun onCorrectPassword(password: String) {
                        appPref.apply {
                            resetLastWrongPasswordCount(clientData.id)
                            saveConfirmedPassword(clientData.id, password)
                        }
                        onNext(true)
                    }

                    override fun onInCorrectPassword(dialog: Dialog?) {
                        val wrongCount = appPref.increaseLastWrongPasswordCount(clientData.id)
                        if (wrongCount == VALUE_MAX_WRONG_PASSWORD_TRY) {
                            dialog?.setOnDismissListener {
                                onNext(false)
                                dialog.setOnDismissListener(null)
                            }
                            dialog?.dismiss()

                        }
                    }

                }
            }.show(activity.supportFragmentManager, ClientLoginBottomSheet.TAG)
        }, onNext)
    }
```

Replace with:

```kotlin
    fun onClientSelect(activity: AppCompatActivity, clientData: ClientData, onNext: (Boolean) -> Unit) {
        handleOpeningNextActivityRequest(clientData, { password ->
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
                        true
                    } else {
                        false
                    }
                }
            )
        }, onNext)
    }
```

- [ ] **Step 2: Remove the now-unused `Dialog` import**

`ClientLoginInteractor.kt` currently has `import android.app.Dialog` at the top, used only by the old `PasswordInputListener.onInCorrectPassword(dialog: Dialog?)` signature. Remove that import line — nothing in the file references `Dialog` anymore. Also remove `import com.khosravi.devin.present.present.ClientLoginBottomSheet` and `import com.khosravi.devin.present.present.ClientLoginBottomSheet.PasswordInputListener` (both gone with the old class).

- [ ] **Step 3: Delete `BaseBottomDialogFragment.kt`**

```bash
rm present-app/src/main/java/com/khosravi/devin/present/arch/BaseBottomDialogFragment.kt
```

- [ ] **Step 4: Confirm no leftover references**

```bash
grep -rn "ClientLoginBottomSheet\.\|PasswordInputListener\|BaseBottomDialogFragment" present-app/src/main
```

Expected: no output. (Note: `ClientLoginSheet` — the new composable's name — will NOT match this grep, which is correct; it's a different identifier from the old `ClientLoginBottomSheet` class.)

- [ ] **Step 5: Verify the full module builds**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit point** — full migration compiles end-to-end. Stop here; ask before committing.

---

### Task 4: Manual verification

**Files:** none (manual testing only, per spec)

- [ ] **Step 1: Install and launch**

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:installDebug
```

Launch the app, navigate to the client-selection screen (`StarterActivity`), and select the client `com.khosravi.sample.devin` (this client's `presenterConfig` sets `logPassword("12346")` in `sample-app`'s `SampleActivity.kt` — you may need `sample-app` installed and its `SampleActivity` launched at least once first so this client is registered/discoverable).

- [ ] **Step 2: Wrong password**

Type any password other than `12346` (e.g. `0000`), tap Confirm (or press the keyboard's Done action). Confirm: the field shows an inline error ("Incorrect password. Please try again."), the sheet stays open, the field is still editable.

- [ ] **Step 3: Password visibility toggle**

Type a password. Tap the `SHOW` button in the field's trailing slot. Confirm: the typed characters become visible (no longer masked with dots). Tap it again (now labeled `HIDE`). Confirm: it re-masks.

- [ ] **Step 4: Correct password**

Type `12346`, submit. Confirm: the sheet closes and the app proceeds to `LogActivity` for that client.

- [ ] **Step 5: Lockout after 4 wrong attempts**

Re-select the same client (you may need to clear the app's stored confirmed-password state first, or use a fresh client — check `AppPref`'s wrong-password-count persistence if the count carries over from Step 2; if Step 2 already used one attempt, only 3 more are needed here). Enter a wrong password 4 times total. Confirm: after the 4th wrong attempt, the sheet closes on its own and a "Too many wrong password try" toast appears, without prompting a 5th time.

- [ ] **Step 6: Dismiss without submitting**

Re-select a password-protected client, then swipe the sheet down (or tap outside it) without entering anything. Confirm: the sheet closes, no crash, the client is not selected (you're still on `StarterActivity` / wherever you triggered it from).

- [ ] **Step 7: Theme check**

Toggle the app's light/dark theme (via `LogActivity`'s overflow menu → "Toggle theme", same as prior migrations), reopen the password sheet in each mode. Confirm text and buttons are legible in both.
