# Multi-Tag Custom Filters — Design

Date: 2026-10-21
Branch: `multi-tag-filters` (targets `presenter-release-4.9.0`)

## Problem

In present-app's log screen a filter chip covers one tag. A custom filter (`CustomFilterItem`) holds a single free-text tag matched with `LIKE %x%` plus optional search text. A user who wants to see logs from several tags together (e.g. `auth`, `network`, `okhttp`) has no way to.

## Decisions

- **Saved, named filter.** Users build a custom filter from several tags and come back to it. It is not an ad-hoc live selection.
- **Exact tags only.** Picked tags match exactly: `tag IN (...)`. No free-text tag pattern.
- **Old custom filters are dropped** on upgrade, along with their pins.
- **One custom-filter type.** `CustomFilterItem` is reshaped; no second filter type.
- **Two entry points, one dialog.** Chip-bar selection mode collects tags and opens the reworked `FilterDialog` with them picked. The toolbar's existing "add filter" opens the same dialog with nothing picked. Saving happens only in the dialog.
- **Per client**, as custom filters are today.
- **Search text** stays optional, is ANDed with the tag set, and matches the log value. The `okhttp` URL-meta search applies only to the plain `okhttp` tag chip, not to custom filters.
- **Scope:** `present-app` only. The provider's raw-query path already accepts a free-form `selection`, so no `devin-write` change is needed.
- **Out of scope:** editing an existing custom filter (issue #138).

## Data model

`filter/CustomFilterCriteria.kt`:

```kotlin
data class CustomFilterCriteria(
    val tags: List<String>,   // exact tags, at least one, de-duplicated, in pick order
    val searchText: String?   // null or blank = no text constraint
)
```

`CustomFilterItem` keeps its shape (`ui` + `criteria`). Its id stays its title, so pinning keeps working through `pinned_filter_ids`.

## Storage — `FilterRepository`

- New prefs file `filter_v2`, one JSON entry per filter keyed by id. Keys as today (`_id`, `_client_id`, `_timestamp`, `_UI._TITLE`). `_CRITERIA` holds `_TAGS` (JSON array of strings) and `_SEARCH_TEXT`.
- `_client_id` is always written. An entry without one is skipped (no more "global" filters).
- An entry with missing or empty `_TAGS`, or one that fails to parse, is skipped; other entries still load.
- **Dropping legacy filters:** on the first `getCustomFilterItemList` call, if the flag `dropped_legacy_filters` (in `pinned_filter_meta`) is false: read the ids in the legacy `filter` prefs file, remove each from `pinned_filter_ids`, clear `filter`, then set the flag. Commit the pin and filter edits before setting the flag.
- `saveFilter` writes to `filter_v2`. Saving a title that already exists for the client overwrites it, as today.
- `removeFilter` removes the entry and also removes its id from `pinned_filter_ids`.
- `clearSync` (toolbar "clear custom filters") clears `filter_v2`.

## Query

- `GetLogsQueryModel` gets `tags: List<String>? = null`.
- `ReaderViewModel.buildQueryGet` for a `CustomFilterItem`: `tags = criteria.tags`, `tag = null`, `value = searchText?.let { Contain(it) }` when not blank.
- `ContentProviderLogsDao` builds the raw-query `selection`: when `tags` is non-empty, append `AND tag IN (?, ?, …)` with one bound argument per tag, next to the existing client-id clause. The existing single `tag` op is unchanged and still used by tag chips.

## UI — selection mode (`LogActivity`)

- The tag chip's long-press quick-action menu (`menu_filter_item_quick_action`) gets a **Select multiple** item, visible only for `TagFilterItem`.
- Choosing it enters selection mode with that tag selected:
  - An action mode (`startSupportActionMode`) shows "N selected", a **Create filter** action and close.
  - Tapping a tag chip toggles its selection; the log list does not change.
  - "All" and custom-filter chips are dimmed and do nothing when tapped.
  - Selected chips show a checked style.
  - Close, back, or deselecting the last tag leaves selection mode.
  - **Create filter** opens `FilterDialog` with the selection picked and leaves selection mode.
- Selection state is a `Set<String>` of tag ids in `LogActivity`, not saved across configuration changes; rotation leaves selection mode.

## UI — `FilterDialog`

- Fields: Title, Tags, Search text (optional), Confirm. The free-text `ed_tag` field is removed.
- Tags: a scrollable `ChipGroup` of checkable chips, one per tag the client has logged.
- Arguments in the bundle (survive rotation): `availableTags: ArrayList<String>` (from `ContentProviderLogsDao.getAllTags` via the ViewModel) and `preselectedTags: ArrayList<String>`. Preselected tags missing from `availableTags` are still shown, checked.
- Validation:
  - No tag checked → error under Tags; nothing saved.
  - Blank title → tags joined with `" + "` (e.g. `auth + network`).
  - No available tags → "No tags logged yet" and Confirm disabled.
- Confirm → `viewModel.addFilter(...)` as today; the new chip becomes selected.

Custom-filter chips show their title; their menu keeps Pin/Unpin and Remove.

## Edge cases

- A picked tag that is no longer logged stays in the filter and matches nothing.
- Hundreds of tags in `IN (...)` stay well under SQLite's bound-argument limit.
- A custom filter titled the same as a tag hides that tag's chip (existing `createTagFilterList` behavior).

## Testing

No new unit tests unless requested (AGENTS.md). Manual check with `sample-app`:

1. Upgrade with existing custom filters: they disappear, and their pins are gone from Settings.
2. Long-press a tag → Select multiple → pick 3 tags → Create filter: dialog shows them checked; title defaults to `a + b + c`.
3. The new chip shows only logs with those exact tags; adding search text narrows them.
4. Toolbar add filter with no tag checked: Confirm shows the error.
5. Pin, unpin and remove the new chip; removing it also removes its pin from Settings.
6. Rotate with the dialog open: picks and fields survive.

## Release

Feature branch `multi-tag-filters` from `presenter-release-4.9.0`, merged back into it. CHANGELOG bullet in 4.9.0: "Create a custom filter from several tags at once: long-press a tag → Select multiple. Previously saved custom filters are removed on upgrade."
