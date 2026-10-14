# Pinned Filter Tags — Design

Date: 2026-10-14
Branch: `pinned-filter-tags` (targets `presenter-release-4.6.0`)

## Problem

present-app can already pin filter chips (tags and custom filters) from the chip quick actions. Pins are stored in the `pinned_filter_ids` SharedPreferences (`FilterRepository`), are global (all clients, this device), and pinned chips sort first in the log screen (`ReaderViewModel.getAllFiltersFlow`).

But a tag can only be pinned after some client has logged it and its chip appears, and a fresh install starts with nothing pinned. Users who always care about a tag like `okhttp` have to find and pin it by hand.

## Decisions

- **Owner:** the present-app user, from the Settings screen. No `presenterConfig` key; clients have no say.
- **One pin list:** Settings shows and edits the same `pinned_filter_ids` list that chip pinning uses. Unpinning anywhere removes the pin everywhere. There is no separate "default" or "locked" list.
- **Pin by name:** a tag can be pinned from Settings by typing it, before any client has logged it.
- **Seed:** `okhttp` (`DevinHttpFlagsApi.LOG_TAG`) is added to the pin list once. If the user removes it, it stays removed. Existing installs get the seed once on upgrade.
- **Scope:** `present-app` only. No changes to `devin`, `devin-no-op`, or the OkHttp modules.

## Data — `FilterRepository`

The pin store stays as is: `pinned_filter_ids` prefs, key = filter id, value = filter title. For a tag both are the tag string, the same as `saveAsPinned(TagFilterItem)` writes today, so a tag pinned from Settings and a tag pinned from its chip are the same entry.

New methods:

- `getPinnedTags(): List<String>` — the pinned ids, sorted alphabetically, for display in Settings. Runs the seed first.
- `pinTag(tag: String)` — trims the input; ignores it if blank; otherwise `putString(tag, tag)` (re-pinning an existing tag is a no-op).
- `unpinTag(tag: String)` — `remove(tag)`.

Seeding:

- A boolean `seeded_default_pins` in a separate small prefs file (`pinned_filter_meta`), not in `pinned_filter_ids` itself, because `getPinnedListIds()` maps every value in that file as a `String`.
- `ensureDefaultPinsSeeded()` (private): if the flag is false, put `okhttp` into the pin list and set the flag. Called at the start of `getPinnedListIds()` and `getPinnedTags()`, so whichever screen reads pins first applies the seed.

Pinned ids that belong to custom filters (whose id is their title) also live in this store. Settings lists every pinned id; removing one unpins it whether it is a tag or a custom filter. This keeps one list, matching the "one pin list" decision.

## Settings UI — `SettingsActivity`

A new "Pinned filter tags" section below the Retention section, in the existing view/XML style:

- A short description: pinned tags show first in the filter bar of every client that logs them.
- A `ChipGroup` with one chip per pinned tag. Each chip has a close icon; tapping it calls `unpinTag` and removes the chip.
- A trailing "Add tag" chip opens an `AlertDialog` with a single-line `EditText`. On confirm, the trimmed text is passed to `pinTag` and the chip group is rebuilt. Blank input and an already-pinned tag do nothing.

`FilterRepository` is already injectable (`@Inject constructor(appContext: Context)`); `SettingsActivity` gets it the same way it gets its other dependencies.

## Log screen

No code change. `FilterRepository.createTagFilterList` already sets `isPinned` from the pin list, and `ReaderViewModel.getAllFiltersFlow` already sorts pinned filters first.

- A pinned tag shows as a chip only for clients that have logs with that tag (chips come from `ContentProviderLogsDao.getAllTags`). A pinned tag no client has logged is invisible in the log screen but stays in Settings.
- The pin list is read whenever filters load, so Settings changes appear the next time a log screen loads its filters.

## Error handling

- `getPinnedListIds()` keeps its existing try/catch returning an empty list.
- Pref writes use `edit { }` (apply). A lost write only loses a pin change; nothing else depends on it.

## Testing

No new tests unless requested (per AGENTS.md). Manual check: fresh install shows `okhttp` in Settings and as the first chip for a client that logs OkHttp traffic; remove it in Settings → chip is unpinned; add it back by typing → pinned again; pin a tag from its chip → it appears in Settings.

## Release

Feature branch `pinned-filter-tags` from `presenter-release-4.6.0`, merged back into it. CHANGELOG bullet in the 4.6.0 release commit, e.g.: "Pin filter tags from Settings, including tags no client has logged yet; `okhttp` is pinned by default."
