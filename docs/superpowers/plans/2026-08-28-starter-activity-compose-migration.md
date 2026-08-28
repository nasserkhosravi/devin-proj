# StarterActivity Compose Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the XML/FastAdapter-based `StarterActivity` (client-selection screen) with a fully Compose `setContent {}` Activity — the first full-Activity migration in `present-app`, after two dialogs (`LogDetailDialog`, `FilterDialog`).

**Architecture:** New `StarterScreen.kt` composable (`Scaffold` + `TopAppBar` + `LazyColumn`) replaces `activity_starter.xml`/`item_client.xml`/`ClientItem` (`FastBindingItem`)/`starter_menu.xml` entirely. `StarterActivity.kt` is rewired to call `setContent { DevinTheme { StarterScreen(...) } }`, with two `mutableStateOf` fields (`message`, `clients`) replacing the old `binding.tvMessage`/`itemAdapter` writes. All business logic (`ReaderViewModel`, `ClientLoginInteractor`, `LogNotificationLaunchCoordinator`, `ClientLoadedState` handling) is untouched — only the view layer changes.

**Tech Stack:** Same Compose stack as the prior two migrations (Kotlin 1.9.0, Compose compiler 1.5.2, Compose BOM 2023.09.02, Material3), plus two new dependencies this task adds: `androidx.activity:activity-compose` (for the `ComponentActivity.setContent {}` extension — the dialogs didn't need this since they used `ComposeView.setContent` directly) and `androidx.compose.material:material-icons-core` (for the refresh icon).

**Testing note:** No unit tests added — pure UI, and per `AGENTS.md` tests/commits require explicit go-ahead. Each task ends with a compile check; behavior is verified manually per the spec's Testing section. Commit points are called out but not executed.

---

### Task 1: Add version catalog entries

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add versions**

In the `[versions]` block, after the existing `composeCompiler = "1.5.2"` line, add:

```toml
activityCompose = "1.7.2"
```

- [ ] **Step 2: Add library aliases**

In the `[libraries]` block, after the existing `compose-material3 = { ... }` line, add:

```toml
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-material-icons-core = { module = "androidx.compose.material:material-icons-core" }
```

Note `compose-material-icons-core` has no `version.ref` — it's managed by the `compose-bom` platform already declared in `present-app/build.gradle.kts`, same as `compose-ui`/`compose-material3`.

- [ ] **Step 3: No build check yet** — unused catalog entries don't break the build. Verified in Task 2 once consumed.

---

### Task 2: Add dependencies to `present-app`

**Files:**
- Modify: `present-app/build.gradle.kts`

- [ ] **Step 1: Add the two new dependencies**

In the `dependencies { }` block, right after the existing `debugImplementation(libs.compose.ui.tooling)` line, add:

```kotlin
implementation(libs.activity.compose)
implementation(libs.compose.material.icons.core)
```

- [ ] **Step 2: Verify the build**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. No source changed yet — this only confirms the new dependencies resolve cleanly (in particular, that `material-icons-core`'s version from the BOM has no conflicts, and `activity-compose:1.7.2` resolves against the existing `compose-ui`/AndroidX stack without errors).

---

### Task 3: Create `StarterScreen.kt`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/StarterScreen.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.present

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.khosravi.devin.present.R
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.uikit.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarterScreen(
    message: String,
    clients: List<ClientData>,
    onClientClick: (ClientData) -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Client selection") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.menu_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = message,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            )
            LazyColumn {
                itemsIndexed(clients, key = { _, client -> client.packageId }) { index, client ->
                    Text(
                        text = client.packageId,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClientClick(client) }
                            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                    )
                    if (index != clients.lastIndex) Divider()
                }
            }
        }
    }
}
```

Note (found in code review): the item key uses `client.packageId` for stable identity across recompositions, the divider check uses `index != clients.lastIndex` (position-based) rather than `client != clients.last()` (value-equality — a latent bug if two clients were ever structurally equal), and the refresh icon's content description reuses the existing `R.string.menu_refresh` instead of a hardcoded string.

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. `StarterScreen` isn't called from anywhere yet, but an unused top-level `@Composable` function doesn't break compilation.

---

### Task 4: Rewire `StarterActivity` and delete old files

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/StarterActivity.kt`
- Delete: `present-app/src/main/res/layout/activity_starter.xml`
- Delete: `present-app/src/main/res/menu/starter_menu.xml`
- Delete: `present-app/src/main/res/layout/item_client.xml`
- Delete: `present-app/src/main/java/com/khosravi/devin/present/client/ClientItem.kt`

- [ ] **Step 1: Replace `StarterActivity.kt` entirely**

```kotlin
package com.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.R
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.ClientLoadedState
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.domain.ClientLoginInteractor
import com.khosravi.devin.present.notification.LogNotificationLaunchCoordinator
import com.khosravi.devin.present.uikit.theme.DevinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

class StarterActivity : BaseActivity() {

    private val notificationLaunchCoordinator = LogNotificationLaunchCoordinator(this) {
        onClientListFetchResult(it)
    }

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var clientLoginInteractor: ClientLoginInteractor

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    private var message by mutableStateOf("")
    private var clients by mutableStateOf(emptyList<ClientData>())

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        notificationLaunchCoordinator.readTarget(intent)

        setContent {
            DevinTheme {
                StarterScreen(
                    message = message,
                    clients = clients,
                    onClientClick = ::onSelectClient,
                    onRefresh = ::refreshClients
                )
            }
        }

        launchGettingClientList()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationLaunchCoordinator.readTarget(intent)
        launchGettingClientList()
    }

    private fun launchGettingClientList() {
        launch {
            message = getString(R.string.loading)
            //delay to let user see loading text a
            delay(100)
            viewModel.getClientList()
                .flowOn(Dispatchers.Main)
                .collect {
                    if (!notificationLaunchCoordinator.requestPermissionIfNeeded(it)) {
                        onClientListFetchResult(it)
                    }
                }
        }
    }

    private fun onSelectClient(clientData: ClientData) {
        viewModel.setSelectedClientId(clientData)
        clientLoginInteractor.onClientSelect(this, clientData) {
            isRouteSuccessful(it)
        }
    }

    private fun onSelectNotificationTarget(target: LogNotificationLaunchCoordinator.Target) {
        viewModel.setSelectedClientId(target.client)
        clientLoginInteractor.onClientSelect(this, target.client) { canRoute ->
            if (canRoute) {
                startActivity(Intent(this, LogActivity::class.java).apply {
                    target.tag?.let { putExtra(LogActivity.EXTRA_TARGET_TAG, it) }
                })
            } else {
                clientLoginInteractor.showManyTryPasswordToast(this)
            }
        }
    }

    private fun refreshClients() {
        launchGettingClientList()
    }

    private fun ClientLoadedState.toStateMessage(): String {
        return when (this) {
            is ClientLoadedState.Zero -> getString(R.string.no_client_found)
            is ClientLoadedState.Single -> getString(R.string.one_client_found)
            is ClientLoadedState.Multi -> getString(R.string.choose_client)
        }
    }

    private fun onClientListFetchResult(loadState: ClientLoadedState) {
        val notificationTarget = notificationLaunchCoordinator.takeTarget(loadState)

        when (loadState) {
            is ClientLoadedState.Single -> {
                val clientData = loadState.client
                clients = listOf(clientData)
                message = loadState.toStateMessage()
                if (notificationTarget != null) {
                    onSelectNotificationTarget(notificationTarget)
                } else {
                    viewModel.setSelectedClientId(clientData)
                    clientLoginInteractor.onClientSelect(this, clientData) {
                        isRouteSuccessful(it)
                    }
                }
            }

            is ClientLoadedState.Multi -> {
                clients = loadState.clients
                message = loadState.toStateMessage()
                notificationTarget?.let(::onSelectNotificationTarget)
            }

            is ClientLoadedState.Zero -> {
                clients = emptyList()
                message = loadState.toStateMessage()
            }
        }
    }

    private fun isRouteSuccessful(canRoute: Boolean) {
        if (canRoute) {
            openNextActivity(this)
        } else {
            clientLoginInteractor.showManyTryPasswordToast(this)
        }
    }

    private fun openNextActivity(activity: AppCompatActivity) {
        activity.startActivity(Intent(activity, LogActivity::class.java))
    }

    companion object {
        const val EXTRA_TARGET_CLIENT_ID = LogNotificationLaunchCoordinator.EXTRA_TARGET_CLIENT_ID
        const val EXTRA_TARGET_TAG = LogNotificationLaunchCoordinator.EXTRA_TARGET_TAG
    }

}
```

Key differences from the old file: no `_binding`/`binding`, no `itemAdapter`/`adapter` (`FastAdapter`/`ItemAdapter`), no `onDestroy` override (nothing to null out), no `onCreateOptionsMenu`/`onOptionsItemSelected` (menu handling deleted), `message`/`clients` are `mutableStateOf` fields instead of view writes, `setContent { DevinTheme { StarterScreen(...) } }` replaces `setContentView(binding.root)` + `setSupportActionBar(binding.toolbar)`.

- [ ] **Step 2: Delete the four old files**

```bash
rm present-app/src/main/res/layout/activity_starter.xml
rm present-app/src/main/res/menu/starter_menu.xml
rm present-app/src/main/res/layout/item_client.xml
rm present-app/src/main/java/com/khosravi/devin/present/client/ClientItem.kt
```

- [ ] **Step 3: Confirm nothing else references the deleted symbols**

```bash
grep -rn "ActivityStarterBinding\|ItemClientBinding\|ClientItem\b\|starter_menu" present-app/src/main
```

Expected: no output. (`ClientItem` was confirmed single-use — only `StarterActivity.kt` and its own file referenced it — before this task was written; this grep re-confirms after the deletion.)

- [ ] **Step 4: Verify the full module builds**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. This is the last file touching the old APIs, so the whole module should build and package cleanly.

- [ ] **Step 5: Commit point** — full migration compiles end-to-end. Stop here; ask before committing.

---

### Task 5: Manual verification

**Files:** none (manual testing only, per spec)

- [ ] **Step 1: Install and launch**

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:installDebug
```

Launch the app. It should land on `StarterActivity` (or skip through it automatically if there's exactly one client and no password — that's existing behavior, unchanged).

- [ ] **Step 2: Zero-client state**

If possible, get to a state with no registered clients (e.g. fresh install with no `sample-app` data ever sent, or after clearing app data). Confirm the message reads "No client data found, run client app or send logs to devin" and the list is empty.

- [ ] **Step 3: Multi-client state**

With `sample-app` (and/or other clients) having sent logs previously, confirm the message reads "Choose client source" and each client's `packageId` appears as a row, separated by dividers (no divider after the last row).

- [ ] **Step 4: Client selection**

Tap a client row. Confirm it proceeds exactly as before — either straight to `LogActivity` (no password set) or through `ClientLoginBottomSheet` (password set), matching pre-migration behavior for that client.

- [ ] **Step 5: Refresh**

Tap the refresh icon in the top app bar. Confirm the message briefly shows "loading" then re-resolves to the correct state.

- [ ] **Step 6: Single-client state**

If only one client is registered, confirm the screen still shows it as expected per `ClientLoadedState.Single` (message "One client found") before the auto-route/auto-prompt logic kicks in — this may be visually brief since `onClientListFetchResult` immediately proceeds to select it.

- [ ] **Step 7: Theme check**

Toggle the app's light/dark theme, relaunch to `StarterActivity` in each mode. Confirm the app bar, message, and list rows are all legible in both.
