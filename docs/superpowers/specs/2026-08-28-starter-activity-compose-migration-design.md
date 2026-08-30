# StarterActivity → Jetpack Compose Migration — Design

Date: 2026-08-28
Branch: `compose-starter-activity` (based on `compose-filter-dialog`, which added `FilterDialog`/`FilterDialogHost` on top of the `compose-log-detail-dialog` foundation)

## Problem

Third step of the gradual `present-app` Compose migration, and the first full Activity (the previous two were dialogs). `StarterActivity` shows the client-selection screen: a `Toolbar` with a refresh action, a status message (`Zero`/`Single`/`Multi` client states), and a `RecyclerView` (`FastAdapter` + `ItemAdapter<ClientItem>`) list of `ClientData.packageId` rows with a divider decoration.

Decision (user-confirmed): full rewrite — the whole Activity body becomes one Compose tree via `setContent {}`, including the toolbar (as a `Scaffold`/`TopAppBar`), not just the list.

## Approach

### 1. Composable screen

New file `present-app/src/main/java/com/khosravi/devin/present/present/StarterScreen.kt`:

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

Requires adding `androidx.compose.material:material-icons-core` (for `Icons.Default.Refresh`) to the version catalog/build file — the only new dependency this migration needs; everything else (`material3`, `runtime`, `ui`) already exists.

Note (found during implementation): the project's pinned `material3` version, `1.1.2` (from `composeBom = "2023.09.02"`), predates `HorizontalDivider` (added in 1.2.0) and has `TopAppBar` marked `@ExperimentalMaterial3Api`. The code above uses the 1.1.2-compatible equivalents instead: `Divider` (the stable predecessor to `HorizontalDivider`) and `@OptIn(ExperimentalMaterial3Api::class)` on `StarterScreen` to allow `TopAppBar`. Bumping the BOM to unlock the newer, non-experimental APIs was considered and rejected as out of scope — it would affect every Compose screen in the app, not just this one.

Text sizing: old XML used `@dimen/textSizeH3` for both the message and each client row (same size, message additionally bold). Compose version uses `headlineSmall` for the message (bold) and `titleMedium` for rows — close visual equivalents from the Material3 type scale rather than a literal sp-for-sp port, consistent with how `LogDetailDialog`/`FilterDialog` used the type scale instead of copying exact old dimens.

### 2. Activity wiring

`StarterActivity.kt` changes:

- `onCreate`: replace binding inflation/`setContentView` with:
  ```kotlin
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
  ```
- Two new `mutableStateOf`-backed fields replace `binding.tvMessage.text` / `itemAdapter.set(...)`:
  ```kotlin
  private var message by mutableStateOf("")
  private var clients by mutableStateOf(emptyList<ClientData>())
  ```
- `onClientListFetchResult`: `Single`/`Multi`/`Zero` branches now set `message = loadState.toStateMessage()` and `clients = listOf(clientData)` / `loadState.clients` / `emptyList()` instead of touching `itemAdapter`/`binding.rvClients`. The `Single` branch's existing auto-select-and-route logic (`onSelectNotificationTarget` / `clientLoginInteractor.onClientSelect`) is unchanged — it still runs regardless of what's shown in the list.
- `launchGettingClientList`: `binding.tvMessage.text = getString(R.string.loading)` becomes `message = getString(R.string.loading)`.
- Removed entirely: `_binding`/`binding` fields, `itemAdapter`/`adapter` (`FastAdapter`/`ItemAdapter<ClientItem>`), `onCreateOptionsMenu`/`onOptionsItemSelected`, the `MaterialDividerItemDecoration` import/usage, `onDestroy`'s `_binding = null`.
- `setSupportActionBar(binding.toolbar)` removed — no more XML action bar; `TopAppBar` inside `StarterScreen` replaces it entirely (this also means `BaseActivity`'s use of `AppCompatActivity`'s action bar APIs elsewhere, if any, isn't affected since nothing else references `StarterActivity`'s action bar).

### 3. Cleanup

Delete `present-app/src/main/res/layout/activity_starter.xml`, `present-app/src/main/res/menu/starter_menu.xml`, `present-app/src/main/res/layout/item_client.xml`, and `present-app/src/main/java/com/khosravi/devin/present/client/ClientItem.kt` (the `FastBindingItem` wrapper — no longer used anywhere once `StarterActivity` stops referencing it; confirmed single-use via grep before deleting).

## Touch Points

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add `compose-material-icons-core` library alias |
| `present-app/build.gradle.kts` | Add the icons dependency |
| `present-app/.../present/StarterScreen.kt` | New — the composable screen |
| `present-app/.../present/StarterActivity.kt` | Rewire `onCreate`/state fields/`onClientListFetchResult`/`launchGettingClientList`; remove menu handling, FastAdapter, ViewBinding |
| `present-app/.../res/layout/activity_starter.xml` | Delete |
| `present-app/.../res/menu/starter_menu.xml` | Delete |
| `present-app/.../res/layout/item_client.xml` | Delete |
| `present-app/.../client/ClientItem.kt` | Delete |

No changes needed: `ReaderViewModel`, `ClientLoginInteractor`, `LogNotificationLaunchCoordinator`, `ClientLoadedState`, `ClientData` — all pure logic/data, untouched.

## Testing

- Manual only, same convention as the previous two migrations.
- Verify: app launch with zero/one/multiple registered clients (use `sample-app` to control this) shows the correct message text in each case.
- Verify: tapping a client row in the multi-client list triggers the existing login flow (`ClientLoginBottomSheet` or direct route, whichever the current password state dictates) — unchanged behavior, just confirming the click plumbing survived the rewrite.
- Verify: refresh icon re-triggers `launchGettingClientList()` (shows "loading" briefly, then re-resolves state).
- Verify: single-client auto-route-or-prompt behavior (the notification-target and auto-select paths in `onClientListFetchResult`) still fires correctly — this logic didn't change, but confirm the state plumbing didn't break it.
- Verify light/dark theme legibility (same as prior migrations).

## Out of Scope

- `ClientLoginBottomSheet` — stays a `BottomSheetDialogFragment`, future migration candidate.
- `LogNotificationLaunchCoordinator` — pure coordination logic, no UI of its own to migrate.
- Any typography/dimension pixel-parity with the old XML — Material3 type scale used instead, same accepted-simplification pattern as prior migrations.
