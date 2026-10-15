# Pinned Filter Tags Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the present-app user pin filter tags from Settings (including tags no client has logged yet), with `okhttp` pinned once by default.

**Architecture:** Reuse the existing `pinned_filter_ids` store in `FilterRepository` as the single pin list. Add tag-level methods (`getPinnedTags`, `pinTag`, `unpinTag`) and a one-time seed, then add a chip-group section to the view-based `SettingsActivity`. The log screen already reads the pin list, so it needs no change.

**Tech Stack:** Kotlin, Android Views + ViewBinding, Material Components (`Chip`, `ChipGroup`, `MaterialAlertDialogBuilder`), SharedPreferences, Dagger.

Spec: `docs/superpowers/specs/2026-10-14-pinned-filter-tags-design.md`

**Project rules (AGENTS.md):** work on branch `pinned-filter-tags` (already created from `presenter-release-4.6.0`). Do **not** add or update unit tests. Do **not** commit until the user says to; the "Commit" steps below are run only after the user approves. Gradle needs JDK 17:
`JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home`

---

## File Structure

- Modify: `present-app/src/main/java/com/khosravi/devin/present/data/FilterRepository.kt` — tag pin methods + one-time seed.
- Modify: `present-app/src/main/res/values/strings.xml` — Settings strings for the new section.
- Modify: `present-app/src/main/res/layout/activity_settings.xml` — "Pinned filter tags" section with a `ChipGroup`.
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/settings/SettingsActivity.kt` — bind the chip group, add/remove pins.

---

### Task 1: Tag pin methods and seed in `FilterRepository`

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/data/FilterRepository.kt`

- [ ] **Step 1: Add the import and the meta prefs field**

Add to the imports:

```kotlin
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
```

Below the existing `pinnedFiltersPref` field add:

```kotlin
    private val pinnedMetaPref =
        appContext.applicationContext.getSharedPreferences(PREF_NAME_PINNED_META, Context.MODE_PRIVATE)
```

- [ ] **Step 2: Seed before reading pins**

Replace the existing `getPinnedListIds()` with:

```kotlin
    fun getPinnedListIds(): List<String> {
        ensureDefaultPinsSeeded()
        try {
            return pinnedFiltersPref.all.map {
                it.value as String
            }.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
```

- [ ] **Step 3: Add the tag methods and the seed**

Below `removeAsPinned(...)` add:

```kotlin
    /** Pinned filter ids (tags and custom filters), sorted, for the Settings screen. */
    fun getPinnedTags(): List<String> = getPinnedListIds().sorted()

    /** Pins [tag] by name, so it can be pinned before any client has logged it. Blank input is ignored. */
    fun pinTag(tag: String) {
        val value = tag.trim()
        if (value.isEmpty()) return
        pinnedFiltersPref.edit { putString(value, value) }
    }

    fun unpinTag(tag: String) {
        pinnedFiltersPref.edit { remove(tag) }
    }

    /** Pins [DEFAULT_PINNED_TAGS] once; a tag the user unpins later stays unpinned. */
    private fun ensureDefaultPinsSeeded() {
        if (pinnedMetaPref.getBoolean(KEY_SEEDED_DEFAULT_PINS, false)) return
        pinnedFiltersPref.edit { DEFAULT_PINNED_TAGS.forEach { putString(it, it) } }
        pinnedMetaPref.edit { putBoolean(KEY_SEEDED_DEFAULT_PINS, true) }
    }
```

- [ ] **Step 4: Add the constants**

In the `companion object`, below `PREF_NAME_PINNED`, add:

```kotlin
        // Separate file: getPinnedListIds() reads every value in PREF_NAME_PINNED as a String.
        private const val PREF_NAME_PINNED_META = "pinned_filter_meta"
        private const val KEY_SEEDED_DEFAULT_PINS = "seeded_default_pins"
        private val DEFAULT_PINNED_TAGS = listOf(DevinHttpFlagsApi.LOG_TAG)
```

- [ ] **Step 5: Compile**

Run: `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

---

### Task 2: Settings strings and layout

**Files:**
- Modify: `present-app/src/main/res/values/strings.xml`
- Modify: `present-app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: Add strings**

In `strings.xml`, after `<string name="settings_storage_calculating">Calculating…</string>`, add:

```xml
    <string name="settings_section_pinned_tags">Pinned filter tags</string>
    <string name="settings_pinned_tags_summary">Pinned tags show first in the filter bar of every app that logs them</string>
    <string name="settings_pinned_tags_add">Add tag</string>
    <string name="settings_pinned_tags_add_hint">Tag, e.g. okhttp</string>
    <string name="settings_pinned_tags_remove">Unpin %1$s</string>
```

- [ ] **Step 2: Add the section to the layout**

In `activity_settings.xml`, inside the inner vertical `LinearLayout`, after the last child (the "Storage used" `LinearLayout`, before the inner `</LinearLayout>`), add:

```xml
            <com.google.android.material.divider.MaterialDivider
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingStart="?attr/spaceLarge"
                android:paddingTop="?attr/spaceLarge"
                android:paddingEnd="?attr/spaceLarge"
                android:paddingBottom="?attr/spaceSmall"
                android:text="@string/settings_section_pinned_tags"
                android:textAppearance="?attr/textAppearanceTitleSmall"
                android:textColor="?attr/colorPrimary" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingStart="?attr/spaceLarge"
                android:paddingEnd="?attr/spaceLarge"
                android:paddingBottom="?attr/spaceSmall"
                android:text="@string/settings_pinned_tags_summary"
                android:textAppearance="?attr/textAppearanceBodySmall" />

            <com.google.android.material.chip.ChipGroup
                android:id="@+id/chipGroupPinnedTags"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingStart="?attr/spaceLarge"
                android:paddingEnd="?attr/spaceLarge"
                android:paddingBottom="?attr/spaceLarge" />
```

- [ ] **Step 3: Compile resources**

Run: `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:processDebugResources`
Expected: `BUILD SUCCESSFUL`

---

### Task 3: Bind pinned tags in `SettingsActivity`

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/settings/SettingsActivity.kt`

- [ ] **Step 1: Imports and injection**

Add imports:

```kotlin
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.chip.Chip
import com.khosravi.devin.present.data.FilterRepository
```

Below the `retentionObserver` field add:

```kotlin
    @Inject
    lateinit var filterRepository: FilterRepository
```

- [ ] **Step 2: Call the binder from `onCreate`**

At the end of `onCreate`, after `loadStorageUsage()`, add:

```kotlin
        bindPinnedTags()
```

- [ ] **Step 3: Add the binder and the add dialog**

Below `loadStorageUsage()` add:

```kotlin
    private fun bindPinnedTags() {
        val group = binding.chipGroupPinnedTags
        group.removeAllViews()
        filterRepository.getPinnedTags().forEach { tag ->
            group.addView(Chip(this).apply {
                text = tag
                isCloseIconVisible = true
                closeIconContentDescription = getString(R.string.settings_pinned_tags_remove, tag)
                setOnCloseIconClickListener {
                    filterRepository.unpinTag(tag)
                    bindPinnedTags()
                }
            })
        }
        group.addView(Chip(this).apply {
            setText(R.string.settings_pinned_tags_add)
            setOnClickListener { showAddPinnedTagDialog() }
        })
    }

    private fun showAddPinnedTagDialog() {
        val input = EditText(this).apply {
            setHint(R.string.settings_pinned_tags_add_hint)
            isSingleLine = true
        }
        val padding = (DIALOG_INPUT_PADDING_DP * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_pinned_tags_add)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                filterRepository.pinTag(input.text.toString())
                bindPinnedTags()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
```

In the `companion object` add:

```kotlin
        private const val DIALOG_INPUT_PADDING_DP = 24
```

- [ ] **Step 4: Build**

Run: `JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

---

### Task 4: Manual verification

- [ ] **Step 1:** Install the debug APK on a device/emulator with a clean app state (`adb shell pm clear com.khosravi.devin.present`), then open Settings. Expected: "Pinned filter tags" shows an `okhttp` chip and an "Add tag" chip.
- [ ] **Step 2:** Run `sample-app`, make an OkHttp request, open its logs in the presenter. Expected: the `okhttp` chip is pinned and sorted right after "All".
- [ ] **Step 3:** In Settings, tap the close icon on `okhttp`. Reopen the client's log screen. Expected: `okhttp` is not pinned. Restart the app: it stays unpinned (seed is not reapplied).
- [ ] **Step 4:** In Settings, add `okhttp` via "Add tag". Expected: chip reappears; log screen shows it pinned. Adding blank text or `okhttp` again changes nothing.
- [ ] **Step 5:** Pin another tag from its chip quick action in the log screen, then open Settings. Expected: that tag is listed.

---

### Task 5: Commit and merge (only after the user says to)

- [ ] **Step 1: Commit**

```bash
git add present-app/src/main/java/com/khosravi/devin/present/data/FilterRepository.kt \
  present-app/src/main/java/com/khosravi/devin/present/present/settings/SettingsActivity.kt \
  present-app/src/main/res/layout/activity_settings.xml \
  present-app/src/main/res/values/strings.xml \
  docs/superpowers/plans/2026-10-14-pinned-filter-tags.md
git commit -m "Pin filter tags from Settings, with okhttp pinned by default"
```

- [ ] **Step 2: Merge into the release branch**

```bash
git switch presenter-release-4.6.0
git merge --no-ff pinned-filter-tags
```

The 4.6.0 CHANGELOG bullet is added later in the release commit, not on this branch.
