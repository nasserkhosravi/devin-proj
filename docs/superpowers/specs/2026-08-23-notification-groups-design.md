# Per-Tag Notification Groups — Design

Date: 2026-08-23
Branch: `notification_log_insert` (builds on top of the single-notification feature already on this branch, see [Notification System] in project memory / `79700dd`)

## Problem

The current notification system (`LatestLogNotificationObserver`) posts a single notification (`NOTIFICATION_ID`) for the whole client, filtered by a flat tag allowlist (`logNotifications.tags`). A client that logs both OkHttp traffic and crash reports gets them all funneled into the same notification slot — the latest one always wins, so a flood of HTTP logs can bury a crash notification.

The ask: let a client define named groups of tags (e.g. "OkHttp", "Crashlytics"), each with its own independent, replace-on-latest notification.

## Config Schema

`presenterConfig.logNotifications` changes from a flat tag allowlist to named groups. This is a breaking change to the schema, acceptable because the current feature is uncommitted/unreleased on this branch.

```json
"logNotifications": {
  "enabled": true,
  "groups": [
    { "name": "OkHttp", "tags": ["dvn_okhttp", "net"] },
    { "name": "Crashlytics", "tags": ["crash", "fatal"] }
  ]
}
```

- `enabled: false` (or `logNotifications` absent) → no notifications at all, same as today.
- `groups` is required when enabled; each group has a `name` (used for the notification id and title) and a non-empty `tags` set.
- **No implicit "others" bucket.** A log whose tag isn't listed in any group produces no notification. This is explicit per product decision — clients opt in per tag, not per catch-all.
- **First-match wins.** If a tag appears in more than one group's `tags` list, the first group in list order is used. Only one notification fires per log.

### Parsing (`ClientDataExt.kt`)

```kotlin
data class NotificationGroup(
    val name: String,
    val tags: Set<String>,
)

data class LogNotificationConfig(
    val isEnabled: Boolean,
    val groups: List<NotificationGroup>,
) {
    fun groupFor(tag: String): NotificationGroup? =
        if (!isEnabled) null else groups.firstOrNull { tag in it.tags }

    companion object {
        val DISABLED = LogNotificationConfig(false, emptyList())
    }
}

fun ClientData.getLogNotificationConfig(): LogNotificationConfig
```

Parsing rules mirror the existing `getLogNotificationConfig()` defensiveness: malformed JSON (missing `name`, non-string tag entries, empty `groups` array) causes that entry — or the whole config — to be dropped rather than throwing. Exact behavior: a malformed *group* is skipped (log a warning); if `groups` is missing/empty entirely while `enabled: true`, treat as `LogNotificationConfig.DISABLED` (nothing to notify on).

`LogNotificationLaunchCoordinator.requestPermissionIfNeeded()`'s check (`clients().any { it.getLogNotificationConfig().isEnabled }`) needs no change — `isEnabled` still exists on the new shape.

## Notification Identity

- **One shared `NotificationChannel`** (existing `CHANNEL_ID`), per your preference — no per-group channels.
- **One notification slot per `(clientId, groupName)` pair.** Notification id: `"$clientId|$groupName".hashCode()`. Deterministic, collision risk acceptable for a local on-device tool (small number of clients/groups).
- **No `setGroup()` / summary notification.** Each group's notification is fully independent in the tray — simplest option, matches "separate notification" intent directly.
- Notification title becomes the group name (was the log tag); body/content stays the latest log's message, same formatting as today (`toNotificationMessage()`, `MAX_MESSAGE_CHARACTERS`).

## Pipeline Changes (`LatestLogNotificationObserver`)

Today: a single `eligibleLogChanges` channel feeds one consumer loop that debounces (`REFRESH_INTERVAL_MILLIS` = 1s, "take latest, discard the rest") into one `publishLatestLog()` call.

This serializes across the whole client — a burst on one tag delays/drops the *other* tag's notification even though they're going to different slots now. Each group needs its own independent debounce.

New structure:
- Change matching now resolves a `NotificationGroup?` via `clientConfigs[change.clientId]?.groupFor(change.tag)` instead of a boolean `includes()` check. No match → drop (no channel send at all).
- Replace the single consumer loop with a `MutableMap<String, Job>` keyed by `"$clientId|$groupName"`, guarded by the same coroutine scope. On each eligible `(change, group)`:
  - Cancel any existing job for that key.
  - Launch a new job: `delay(REFRESH_INTERVAL_MILLIS)`, then re-check the map for a newer pending change for that key isn't needed since cancel/relaunch already collapses bursts to the latest — just publish `change` for `(clientId, group)` after the delay.
- `publishLatestLog(change, group)`: re-fetch the log by id (`ContentProviderLogsDao.getLog`), verify it still matches `change.clientId`/`change.tag` (existing guard), build notification with `group.name` as title, `notificationId = "$clientId|${group.name}".hashCode()`.
- The existing `observedLogChanges` → wait-for-config-generation → re-check `groupFor()` path is unchanged in shape, just swaps `includes()` for `groupFor()`.

Map cleanup: entries are cheap (one `Job` reference each), no explicit eviction needed — bounded by number of distinct `(clientId, groupName)` pairs ever seen, which is small.

## Tap-Through

Unchanged. `createContentIntent(clientId, tag)` still uses the *triggering log's tag* (not the group name) — `EXTRA_TARGET_CLIENT_ID` + `EXTRA_TARGET_TAG` flow into `LogNotificationLaunchCoordinator` → `StarterActivity` → `LogActivity`'s existing filter-chip auto-select. No changes needed outside `LatestLogNotificationObserver` and `ClientDataExt.kt` for this part.

## Touch Points

| File | Change |
|---|---|
| `present-app/.../client/ClientDataExt.kt` | Replace `LogNotificationConfig(isEnabled, allowedTags)` + `includes()` with `LogNotificationConfig(isEnabled, groups)` + `groupFor()`. New `NotificationGroup` data class. Rewrite `getLogNotificationConfig()` parsing. |
| `present-app/.../notification/LatestLogNotificationObserver.kt` | Swap `includes()` calls for `groupFor()`. Replace single debounce loop with per-group-key `Job` map. `publishLatestLog` takes a resolved `NotificationGroup`, computes per-group notification id, uses group name as title. |
| `sample-app/.../SampleActivity.kt` | Update demo `presenterConfig` to the new `groups` shape. |

No changes needed: `DevinUriHelper`, `DevinContentProvider`, `LogActivity`, `StarterActivity`, `LogNotificationLaunchCoordinator` — the `LogChange(id, clientId, tag)` plumbing and tap-through already carry everything required.

## Testing

- Unit-test `ClientDataExt.getLogNotificationConfig()` parsing: valid groups, missing `name`, non-string tag, empty `groups`, `enabled: false`, absent `logNotifications`, tag matching two groups (first-match).
- Unit-test `LogNotificationConfig.groupFor()`: match, no-match, disabled.
- Manual verification (per-group debounce + independent notification ids) via the sample app: configure two groups, fire logs on both tags rapidly, confirm two separate notifications appear/update independently rather than one clobbering the other.

## Out of Scope

- Summary/stacked notifications (explicitly deferred — "whatever is simpler").
- Cross-client group merging (each client's groups are independent, keyed by `clientId`).
- Per-group notification channels (explicitly deferred — one shared channel).
- Unseen-count / inbox-style stacking within a group (explicitly deferred — latest-only replace).
