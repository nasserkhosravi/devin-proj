# Filter Actions FAB — Design

Date: 2026-11-19
Issue: #191
Branch: `filter-actions-fab` (targets `presenter-release-4.11.0`)

## Problem

A filter's actions (Pin/Unpin, Share as JSON, Select multiple, Edit, Remove) are reachable only by
long-pressing its chip in `FilterChipsRow.kt`. Long press is a hidden gesture: users who never find it
don't know the actions exist.

## Goal

A floating action button (FAB) on the log screen opens the actions for the **currently selected filter**,
so they are visible without knowing the gesture.

- Non-goals: changing what any action does, new actions, changing the chips' long-press menu.
- Anti-goals: the FAB hides the last log rows with no way to see them; the FAB menu and the long-press menu
  drift apart (different items, order or labels); an alpha library ships in the release.

## Decisions (user)

- **Stable Material 3 only.** The project's Compose BOM `2025.12.01` gives material3 `1.4.0`, which has no
  `FloatingActionButtonMenu` (only in 1.5.0 alphas). Build the menu from stable pieces: a
  `FloatingActionButton` that expands into labelled items stacked above it. No dependency change.
- **Target `presenter-release-4.11.0`.** `present-app` only.
- **"All" (index filter) selected → FAB offers only "Create filter".** The FAB stays visible there.

## Menu content

Per selected filter, in this order (the long-press menu's items, same labels and order, minus Select multiple):

| Selected filter | Items |
|---|---|
| `TagFilterItem` | Pin/Unpin, Share as JSON, Create filter |
| `CustomFilterItem` | Pin/Unpin, Edit, Remove, Create filter |
| `IndexFilterItem` ("All") | Create filter |

- Pin/Unpin label follows `filter.ui.isPinned` (`menu_action_pin` / `menu_action_unpin`).
- Each item fires the same thing as the long-press menu: `FilterChipAction.TogglePin`, `.ShareAsJson`,
  `.Edit`, `.Remove(listOf(filter))` through `onFilterAction`.
- **No Select multiple in the FAB** (user, after trying it): keeps the menu short. It stays in the chip's
  long-press menu.
- **Create filter** fires `FilterChipAction.CreateFilter(tags)`. *(My call)* With a tag selected, that tag is
  preselected (`listOf(tag.tagValue)`); otherwise the list is empty (same as the toolbar's
  "Create custom filter"). Label: `menu_create_filter`.

**One source of truth.** The chip's `DropdownMenu` and the FAB menu build their items from one shared
function (e.g. `filterMenuEntries(filter, …)` returning label + onClick entries), so they can't drift. The
FAB leaves out Select multiple and adds "Create filter" at the end; the chip menu does neither.
- **Icons** (user): each FAB item shows an icon next to its label (pin/keep-off, share, edit, delete, add).
  The long-press menu stays text-only.

## Which filter is "selected"

`filters.find { it.id == selectedFilterId }`, the same lookup `LogActivity.optCurrentFilterItem()` uses.
If none is found (e.g. list still loading), treat it as "All": Create filter only.

## Behaviour

- **Placement:** bottom-end, through `Scaffold`'s `floatingActionButton` slot (it already respects the
  system bars and, via `contentWindowInsets`, the keyboard).
- **FAB icon:** a filter/tune-style icon when collapsed, a close (X) icon when expanded. Content descriptions:
  "Filter actions" / "Close filter actions" (new strings).
- **Expanded menu:** items stacked vertically above the FAB, right-aligned, each a small pill/extended-FAB
  style button with its label (e.g. `SmallExtendedFloatingActionButton`-like look built from
  `ExtendedFloatingActionButton` or `Surface` + `Text`, whatever 1.4.0 offers stably). A light scrim covers the
  screen behind the menu; tapping the scrim collapses it. Back press collapses it (`BackHandler`, enabled only
  while expanded). Picking an item collapses the menu, then runs the action.
- **Collapse when the target changes:** if the selected filter changes while expanded, collapse.
- **Hidden when:**
  - tag selection mode is on (`selectedTags != null`; that mode has its own bar),
  - the filter row is disabled (`isFilterRowEnabled == false`, e.g. during refresh),
  - the user is scrolling down the log list; it shows again on scroll up or at the top
    (hide/show with an animated slide/fade, e.g. `AnimatedVisibility`). Leaving selection mode shows it again,
    even if the list was scrolled down during selection.
- **Last rows reachable:** the `LazyColumn` gets bottom `contentPadding` large enough (~88.dp: FAB 56 + margins)
  that the last row can scroll above the FAB.
- **Long press on chips** keeps working unchanged.

## Analytics

None new. The actions already log their own analytics in `LogActivity`.

## Left to the builder

- Exact visuals of the expanded items, animation specs, scrim alpha, icon choice (from `material-icons` already
  used, or a drawable in `res/drawable`).
- Where the FAB composable lives (suggested: new file `present/FilterActionsFab.kt`) and the shape of the shared
  menu-entry model.
- No unit tests (AGENTS.md: tests only when the user asks).

## Acceptance

- A FAB is visible on the log screen and runs the filter actions on the selected filter.
- The actions behave exactly like the long-press menu (reuse `FilterChipAction`, shared entry list).
- "All" selected → only Create filter. Selection mode / disabled filter row → no FAB.
- The last log row can be scrolled clear of the FAB; the FAB hides on scroll down.
- No regression in the long-press menu.
