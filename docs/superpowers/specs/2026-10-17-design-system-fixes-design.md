# present-app design-system fixes (contrast, theme, type, spacing) — design

Date: 2026-10-17
Branch: `compose-migration`

## Context (from the 2026-10-17 design review)

- Compose `DevinTheme` uses Material3's **baseline** scheme with only primary/secondary/tertiary set to
  Android Studio template purples (`uikit/theme/Color.kt`), while `res/values/colors.xml` +
  `values-night/colors.xml` define the app's real Material 3 palette (used by the XML `Theme.App`).
  Window background `#FEF7FF` vs Compose background `#FFFBFE` → two sources of truth.
- Row composables read XML colours directly with `colorResource(R.color.…)` and some hard-coded
  `Color.Gray/Red/Yellow/Green`.
- XML shapes (12/26/24dp) differ from Compose default shapes; composables hard-code corner radii.
- 10sp text (log-level badge, tag chip); explicit `fontSize` values instead of type-scale tokens.
- Off-grid spacing (6dp ×3, 10dp ×5).
- WCAG AA failures (4.5:1 normal text, 3:1 large text/icons).

## Decisions

### 1. One colour source = `colors.xml` (+ `values-night`)
- `DevinTheme` builds its `ColorScheme` **from the XML colour resources** (via `colorResource`), so the
  XML theme and Compose share one palette and dark mode follows the night resources:
  `lightColorScheme(...)`/`darkColorScheme(...)` chosen by `isSystemInDarkTheme()`, with **every role that
  `colors.xml` defines** mapped (primary, onPrimary, primaryContainer, onPrimaryContainer, secondary…,
  surface, onSurface, surfaceVariant, onSurfaceVariant, outline, outlineVariant, background). Add the
  missing roles to both colors.xml files where needed for Compose (at least `on_background` = on_surface,
  `error`/`on_error`/`error_container`/`on_error_container` using M3 baseline values
  light `#B3261E/#FFFFFF/#F9DEDC/#410E0B`, dark `#F2B8B5/#601410/#8C1D18/#F9DEDC`; tertiary set:
  light `#7D5260/#FFFFFF/#FFD8E4/#31111D`, dark `#EFB8C8/#492532/#633B48/#FFD8E4`).
  Delete the template purples in `Color.kt`.
- App-specific colours (log levels, chips, okhttp chip, session-start, JSON syntax, HTTP status,
  search highlight, search counter, update footer) go into an `@Immutable data class DevinColors(...)`
  provided by `LocalDevinColors` from `DevinTheme`, also **built from the XML resources**, and accessed as
  `MaterialTheme.devinColors` (same pattern as `MaterialTheme.spacing`). Composables no longer call
  `colorResource(R.color.…)` or hard-coded `Color.*` for UI colours.
- `JsonConfigColor.create(context)` keeps reading the same XML resources (spans are built from a Context),
  so JSON colours stay single-sourced too.
- Remove now-unused colours (`text_primary`/`text_tertiary` if replaced by scheme roles, etc.) only if
  nothing else (XML themes, JsonConfigColor, notifications) uses them.

### 2. Contrast fixes (all values verified ≥ 4.5:1 on their real background)
| Element | Light | Dark |
|---|---|---|
| HTTP error status text (`status_error`) | `#B3261E` (6.2) | `#F2B8B5` (10.0) |
| okhttp chip selected: text on `#00ACC1` | text `#00363D` (4.8) | unchanged |
| Secondary text: timestamps, HTTP host (was `text_tertiary` / `Color.Gray`) | `colorScheme.onSurfaceVariant` (8.9) | `onSurfaceVariant` (10.0) |
| Search match (all) | text `#1C1B1F` on `#FFE082` (13.2) | text `#FFF8E1` on `#5D4300` (8.7) |
| Search match (current) | text `#1C1B1F` on `#FFB74D` (9.9) | text `#1C1B1F` on `#FFB74D` (9.9) |
| Search counter bar | text `#FFFFFF` on `#146C3A` (6.5) | text `#003919` on `#7DDC9F` (7.9) |
| Session-start pill (overlay) | white 12% overlay; pill text `#FFFFFF` (5.6) | same |
| JSON number/null | `#BF360C` (5.6) | unchanged `#FF8A65` |
| JSON boolean | `#00796B` (5.3) | unchanged `#65D3E3` |
Keep search highlight underline. Add the new colours as XML resources (light + night) and read them via
`DevinColors`. Add `status_success` to values-night if it's referenced.

### 3. Shapes — one scale
`DevinTheme` sets `MaterialTheme(shapes = Shapes(extraSmall = 4.dp, small = 12.dp, medium = 16.dp,
large = 24.dp, extraLarge = 28.dp))` (rounded). Align XML `ShapeAppearance.LoggerApp.*` to the same
values (Small 12, Medium 16, Large 24). Replace hard-coded `RoundedCornerShape(n.dp)` in composables with
the closest `MaterialTheme.shapes.*` (pill/`50%`/CircleShape stay as they are — they are intentional).

### 4. Typography — use the type scale, nothing below 11sp
`DevinTheme` passes `typography = Typography()` explicitly (M3 defaults). Replace explicit `fontSize`:
10sp badge → `labelSmall` (11sp); 10sp tag chip → `labelMedium` (12sp); 12sp → `bodySmall` or
`labelMedium` (pick by role: metadata = bodySmall, chip/badge label = labelMedium); 14sp → `bodyMedium`;
16sp → `bodyLarge`/`titleMedium` (by role). Keep existing weights (copy(fontWeight=…)) where they were set.
Fira Code monospace for HTTP body stays.

### 5. Spacing — 4dp grid
Snap off-grid values: 6dp → 4dp or 8dp, 10dp → 8dp or 12dp (nearest visual match; prefer the spacing
tokens `MaterialTheme.spacing.*`). Hard-coded paddings that equal a token use the token. Component sizes
(icon 20/24/40dp, status column 56dp, 48dp row min) stay.

## Out of scope
Layout changes, new screens, string changes, XML-only notification styling, dynamic colour.

## Verification
- Build + `testDebugUnitTest` (JsonSpanTextUtilTest etc. must still pass — JsonConfigColor colours change
  only in value).
- Re-run the contrast computation on the final values (script in the review).
- Device (emulator-5554): screenshots of starter, log list (with each log level, okhttp chip selected,
  session-start row), log detail, export dialog, settings, HTTP detail (JSON body + active search) in
  **light and dark**; nothing unreadable, no text below 11sp, colours consistent between window and
  Compose background on launch.
