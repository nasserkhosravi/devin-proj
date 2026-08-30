# LogActivity Compose Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `LogActivity`'s XML/FastAdapter UI (filter chips, paginated log list, search, menus) with Jetpack Compose, without touching `ReaderViewModel` and without breaking `ImportLogActivity` (still XML/FastAdapter, unmigrated, transitively depends on several of the same item-view classes).

**Architecture:** `LogActivity` becomes a thin state holder (`setContent { DevinTheme { LogScreen(...) } }`), same shape as the already-migrated `StarterActivity`. New composables live under `present-app/.../present/` (`LogScreen.kt`, `FilterChipsRow.kt`) and `present-app/.../present/logitem/` (one file per row type). A new `LogListRowItem` sealed model (in the `log` package) distinguishes the search placeholder row from real `LogItemData` rows. Only `FilterItemViewHolder`, `SearchItemView`, `EndlessScrollListener`, and the `SingleSelectionItemAdapter` usage in `LogActivity` are deleted — confirmed via full-codebase grep to be exclusive to `LogActivity`. `TextLogItem`, `HttpLogItemView`, `ImageLogItem`, `SessionStartLogItem`, `HeaderLogDateItem`, `ReplicatedTextLogItem`/`TextLogSubItem`, and `AppExt.toItemViewHolder()` are all **kept** because `ImportLogActivity` depends on them.

**Tech Stack:** Kotlin 1.9.0, Compose compiler 1.5.2, Compose BOM 2023.09.02, Compose Material3 1.1.2, `material-icons-core` (already a dependency — no new deps needed for this plan). Existing Glide 4.16.0 (no Glide-Compose dependency added).

**Testing note:** No unit/instrumented tests are added — this is pure UI, and per `AGENTS.md` tests/commits require your explicit go-ahead. Each task ends with a compile check; UI behavior is verified manually in Task 13. Commit points are called out but not executed — you run `git commit` yourself when ready.

**Spec:** `docs/superpowers/specs/2026-08-30-log-activity-compose-migration-design.md`

---

### Task 1: Create `LogListRowItem.kt`

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/log/LogListRowItem.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.log

sealed interface LogListRowItem {
    data class Search(val hint: String, val text: String?) : LogListRowItem
    data class Row(val data: LogItemData) : LogListRowItem
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Create `DateHeaderRow.kt`

Ports `HeaderLogDateItem`/`item_header_log_date.xml` (a centered Chip-styled pill using `?attr/colorTertiary`/`?attr/colorOnTertiary`).

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logitem/DateHeaderRow.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun DateHeaderRow(calendar: CalendarProxy, data: DateLogItemData) {
    val dateText = calendar.initIfNeed(data.presentDate).getFormatted()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.small),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.tertiary,
        ) {
            Text(
                text = dateText,
                color = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Create `TextLogRow.kt`

Ports `TextLogItem`/`item_log.xml`: a divider above, a 40dp circular level icon, a level badge pill + timestamp + optional tag chip row, then the message text. Log-level → (label, icon, colors) mapping ported verbatim from `TextLogItem.styleIt`.

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logitem/TextLogRow.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.present.logitem

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.uikit.theme.spacing

private data class LogLevelStyle(
    val label: String,
    val icon: Int,
    val iconColor: Int,
    val iconBgColor: Int,
    val badgeBgColor: Int,
    val badgeTextColor: Int,
)

@Composable
private fun logLevelStyle(logLevel: Int): LogLevelStyle? = when (logLevel) {
    Log.ERROR -> LogLevelStyle(
        stringResource(R.string.log_item_error), R.drawable.ic_info_24px,
        R.color.log_error_icon, R.color.log_error_icon_bg, R.color.log_error_container, R.color.log_error_text
    )

    Log.WARN -> LogLevelStyle(
        stringResource(R.string.log_item_warn), R.drawable.ic_warning_24px,
        R.color.log_warning_icon, R.color.log_warning_icon_bg, R.color.log_warning_container, R.color.log_warning_text
    )

    Log.INFO -> LogLevelStyle(
        stringResource(R.string.log_item_info), R.drawable.ic_info_24px,
        R.color.log_info_icon, R.color.log_info_icon_bg, R.color.log_info_container, R.color.log_info_text
    )

    Log.DEBUG -> LogLevelStyle(
        stringResource(R.string.log_item_debug), R.drawable.ic_bug_report_24px,
        R.color.log_debug_icon, R.color.log_debug_icon_bg, R.color.log_debug_container, R.color.log_debug_text
    )

    Log.VERBOSE -> LogLevelStyle(
        stringResource(R.string.log_item_verbose), R.drawable.ic_bug_report_24px,
        R.color.log_debug_icon, R.color.log_debug_icon_bg, R.color.log_debug_container, R.color.log_debug_text
    )

    else -> null
}

@Composable
fun TextLogRow(
    calendar: CalendarProxy,
    data: TextLogItemData,
    ignoreTagChip: Boolean,
    onClick: () -> Unit,
) {
    val dateText = calendar.initIfNeed(data.timePresent).getFormatted()
    val style = logLevelStyle(data.logLevel)

    Column(modifier = Modifier.fillMaxWidth()) {
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.small)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = style?.let { colorResource(it.iconBgColor) } ?: MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                style?.let {
                    Icon(
                        painter = painterResource(it.icon),
                        contentDescription = null,
                        tint = colorResource(it.iconColor),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = MaterialTheme.spacing.large)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    style?.let {
                        Text(
                            text = it.label.uppercase(),
                            fontSize = 10.sp,
                            color = colorResource(it.badgeTextColor),
                            modifier = Modifier
                                .background(colorResource(it.badgeBgColor), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = dateText,
                        fontSize = 12.sp,
                        color = colorResource(R.color.text_tertiary),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                    if (!ignoreTagChip) {
                        Text(
                            text = data.tag,
                            fontSize = 10.sp,
                            color = colorResource(R.color.on_secondary_container),
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .background(colorResource(R.color.secondary_container), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = data.text,
                    fontSize = 14.sp,
                    color = colorResource(R.color.text_primary),
                    modifier = Modifier.padding(top = MaterialTheme.spacing.xs)
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. (Note: `Divider` is used deliberately, not `HorizontalDivider` — Material3 1.1.2 doesn't have the latter, per this migration's established gotcha.)

---

### Task 4: Create `HttpLogRow.kt`

Ports `HttpLogItemView`/`item_http.xml`. One faithfulness detail: `HttpLogItemView.createBinding` sets `tvDomain`'s color to `Color.GRAY` in code, which runs *after* the XML's `@color/text_secondary` is applied and is never overridden again in `bindView` — so the actually-displayed domain color is gray, not `text_secondary`. Ported as `Color.Gray` here, not `colorResource(R.color.text_secondary)`.

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logitem/HttpLogRow.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.http.HttpLogOperationStatus
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun HttpLogRow(calendar: CalendarProxy, data: HttpLogItemData, onClick: () -> Unit) {
    val statusColorRes: Int
    val statusText: String
    when (val operationStatus = data.data.operationStatus) {
        is HttpLogOperationStatus.Respond -> {
            val statusCode = operationStatus.status
            statusText = statusCode.toString()
            statusColorRes = if (statusCode in 400..600) R.color.status_error else R.color.text_primary
        }

        HttpLogOperationStatus.Requested -> {
            statusText = "Requested"
            statusColorRes = R.color.text_primary
        }

        HttpLogOperationStatus.NetworkFailed -> {
            statusText = "!!!"
            statusColorRes = R.color.status_error
        }

        HttpLogOperationStatus.Unsupported -> {
            statusText = ""
            statusColorRes = R.color.text_primary
        }
    }
    val statusColor = colorResource(statusColorRes)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.xs)
        ) {
            Text(text = statusText, color = statusColor, modifier = Modifier.width(56.dp))
            Column {
                Text(text = data.getL1SummeryText(), color = statusColor)
                Text(text = data.getFullDomainText(), color = Color.Gray)
                Text(text = data.getTimeText(calendar), color = colorResource(R.color.text_primary))
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Create `SessionStartRow.kt`

Ports `SessionStartLogItem`/`item_session_start.xml`: a colored bar with a dot, "Session started" bold label, an optional version chip, a date chip, and a right-aligned time — all pill-chip text colors/backgrounds ported from the XML's exact hex values (`session_start_*` colors, `#33FFFFFF` chip background).

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logitem/SessionStartRow.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.log.SessionStartLogItemData

@Composable
fun SessionStartRow(calendar: CalendarProxy, data: SessionStartLogItemData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.session_start_background))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colorResource(R.color.session_start_dot), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.session_started),
            color = colorResource(R.color.session_start_text),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        val versionName = data.appVersionName
        if (!versionName.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.session_version, versionName),
                color = colorResource(R.color.session_start_secondary_text),
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Text(
            text = calendar.initIfNeed(data.datePresent).getFormatted(),
            color = colorResource(R.color.session_start_secondary_text),
            fontSize = 12.sp,
            modifier = Modifier
                .padding(start = 10.dp)
                .background(Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = calendar.initIfNeed(data.timePresent).getFormatted(),
            color = colorResource(R.color.session_start_secondary_text),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit point** — five standalone row composables done, each independently compiling. Stop here; ask before committing if you want a checkpoint before the more complex remaining pieces.

---
### Task 6: Create `ImageLogRow.kt`

Ports `ImageLogItem`/`item_image_log.xml`: an info text block (status/name/date/time, plus image/file size once loaded), a click-to-copy URL line, and the image itself. Per the spec decision, image loading reuses the existing `Glide...CustomTarget<File>` API (no Glide-Compose dependency) via a `LaunchedEffect` writing into local `mutableState`.

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logitem/ImageLogRow.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.present.logitem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.humanReadableByteCountSI
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.setClipboard
import com.khosravi.devin.present.uikit.theme.spacing
import com.khosravi.devin.read.DevinImageFlagsApi
import java.io.File

private sealed interface ImageLoadState {
    data object Loading : ImageLoadState
    data class Loaded(val bitmap: Bitmap, val fileLength: Long) : ImageLoadState
    data object Failed : ImageLoadState
}

@Composable
fun ImageLogRow(calendar: CalendarProxy, data: ImageLogItemData) {
    val context = LocalContext.current
    var loadState by remember(data.data.url) {
        mutableStateOf<ImageLoadState>(if (data.data.isFailed()) ImageLoadState.Failed else ImageLoadState.Loading)
    }

    LaunchedEffect(data.data.url) {
        if (data.data.isFailed()) return@LaunchedEffect
        Glide.with(context).asFile().load(data.data.url).into(object : CustomTarget<File>() {
            override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                val bitmap = BitmapFactory.decodeFile(resource.path)
                loadState = if (bitmap != null) ImageLoadState.Loaded(bitmap, resource.length()) else ImageLoadState.Failed
            }

            override fun onLoadCleared(placeholder: Drawable?) {}

            override fun onLoadFailed(errorDrawable: Drawable?) {
                loadState = ImageLoadState.Failed
            }
        })
    }

    val (statusText, statusColorRes) = when (data.data.status) {
        DevinImageFlagsApi.Status.SUCCEED -> stringResource(R.string.image_status_success) to R.color.status_success
        DevinImageFlagsApi.Status.FAILED -> stringResource(R.string.image_status_failed) to R.color.status_error
        else -> "" to android.R.color.black
    }

    // stringResource() is @Composable and can't be called inside the buildAnnotatedString{} lambda below
    // (that lambda's receiver type isn't @Composable) — resolve every string here first.
    val statusPrefix = stringResource(R.string.image_tag_status_prefix)
    val loadingText = stringResource(R.string.image_tag_loading)
    val namePrefix = stringResource(R.string.image_tag_name_prefix)
    val datePrefix = stringResource(R.string.image_tag_date_prefix)
    val timePrefix = stringResource(R.string.image_tag_time_prefix)
    val imageSizePrefix = stringResource(R.string.image_tag_image_size_prefix)
    val fileSizePrefix = stringResource(R.string.image_tag_file_size_prefix)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.small)
    ) {
        val state = loadState
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(statusPrefix) }
                if (state is ImageLoadState.Loading) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(loadingText) }
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colorResource(statusColorRes))) { append(statusText) }
                    append("\n")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(namePrefix) }
                    append(data.data.name + "\n")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(datePrefix) }
                    append(calendar.initIfNeed(data.datePresent).getFormatted() + "\n")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(timePrefix) }
                    append(calendar.initIfNeed(data.timePresent).getFormatted())
                    if (state is ImageLoadState.Loaded) {
                        append("\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(imageSizePrefix) }
                        append("${state.bitmap.width}x${state.bitmap.height} \n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(fileSizePrefix) }
                        append(humanReadableByteCountSI(state.fileLength))
                    }
                }
            }
        )

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("URL: ") }
                append(data.data.url)
            },
            modifier = Modifier.clickable {
                Toast.makeText(context, "URL Copied", Toast.LENGTH_SHORT).show()
                context.setClipboard(data.data.url)
            }
        )

        if (state is ImageLoadState.Loaded) {
            Image(bitmap = state.bitmap.asImageBitmap(), contentDescription = data.data.name)
        }

        Divider(modifier = Modifier.padding(top = MaterialTheme.spacing.small))
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---
### Task 7: Create `SearchBarRow.kt`

Ports `SearchItemView`/`item_search.xml`. Per the spec decision, search stays as a real row in the list (index 0) rather than a separate pinned slot — `LogActivity` (Task 10) is responsible for placing it there. This composable is just the input itself: a `TextField` firing `onTextChange` on every keystroke, replacing `SearchView`'s query listener (the existing 700ms debounce stays in `LogActivity`, unchanged).

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/logitem/SearchBarRow.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun SearchBarRow(
    text: String?,
    hint: String,
    onTextChange: (String?) -> Unit,
) {
    OutlinedTextField(
        value = text ?: "",
        onValueChange = { onTextChange(it.ifEmpty { null }) },
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.xs)
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. (`Icons.Default.Search` is confirmed present in the installed `material-icons-core` 1.6.8 — checked its sources jar directly, unlike `Visibility`/`VisibilityOff` which were missing in an earlier migration.)

---

### Task 8: Create `FilterChipsRow.kt`

Ports `FilterItemViewHolder`/`item_filter.xml` plus `LogActivity.showFilterContextMenu`/`normalizeMenuToItsAvailableActions` (the long-press popup menu). Per the spec decision, the 2-row wrap (`StaggeredGridLayoutManager`) behavior for >4 filters is dropped — this is always a horizontally-scrollable single row.

Visibility rules ported verbatim from `normalizeMenuToItsAvailableActions`: `TagFilterItem` gets pin/unpin + share-as-json (no remove); `CustomFilterItem` gets pin/unpin + remove (no share); `IndexFilterItem` gets no context menu at all (long-click disabled, matching `showFilterContextMenu`'s early return on `isIndexFilterItem()`). Pin/unpin is mutually exclusive based on current `ui.isPinned`.

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/FilterChipsRow.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.present

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.filter.isIndexFilterItem
import com.khosravi.devin.present.uikit.theme.spacing
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi

sealed interface FilterChipAction {
    data class TogglePin(val item: FilterItem) : FilterChipAction
    data class ShareAsJson(val item: TagFilterItem) : FilterChipAction
    data class Remove(val item: CustomFilterItem) : FilterChipAction
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterChipsRow(
    filters: List<FilterItem>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (FilterItem) -> Unit,
    onAction: (FilterChipAction) -> Unit,
) {
    LazyRow(modifier = Modifier.padding(vertical = MaterialTheme.spacing.xs)) {
        items(filters, key = { it.id }) { filter ->
            FilterChipView(
                filter = filter,
                isSelected = filter.id == selectedId,
                enabled = enabled,
                onSelect = { onSelect(filter) },
                onAction = onAction,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterChipView(
    filter: FilterItem,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onAction: (FilterChipAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isOkHttp = filter is TagFilterItem && filter.tagValue == DevinHttpFlagsApi.LOG_TAG
    val label = if (isOkHttp) "http" else filter.ui.title.value

    val bgColor = if (isOkHttp) {
        colorResource(if (isSelected) R.color.tag_okhttp_container_selected else R.color.tag_okhttp_container_unselected)
    } else {
        colorResource(if (isSelected) R.color.chip_selected else R.color.chip_unselected)
    }
    val textColor = if (isOkHttp) {
        colorResource(if (isSelected) R.color.tag_okhttp_text_selected else R.color.tag_okhttp_text_unselected)
    } else {
        colorResource(if (isSelected) R.color.chip_text_selected else R.color.chip_text_unselected)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xs)
            .background(bgColor, RoundedCornerShape(50))
            .combinedClickable(
                enabled = enabled,
                onClick = onSelect,
                onLongClick = if (filter.isIndexFilterItem()) null else {
                    { menuOpen = true }
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (filter.ui.isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_keep_24px),
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Text(text = label, color = textColor)

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (filter.ui.isPinned) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_unpin)) }, onClick = {
                    menuOpen = false
                    onAction(FilterChipAction.TogglePin(filter))
                })
            } else {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_pin)) }, onClick = {
                    menuOpen = false
                    onAction(FilterChipAction.TogglePin(filter))
                })
            }
            if (filter is TagFilterItem) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_share_as_json)) }, onClick = {
                    menuOpen = false
                    onAction(FilterChipAction.ShareAsJson(filter))
                })
            }
            if (filter is CustomFilterItem) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_remove)) }, onClick = {
                    menuOpen = false
                    onAction(FilterChipAction.Remove(filter))
                })
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit point** — all row/chip composables done. Stop here; ask before committing before wiring up the orchestrating `LogScreen`.

---
### Task 9: Create `LogScreen.kt`

The orchestrating composable: `Scaffold` + `TopAppBar` (2 icon actions + overflow menu, replacing `menu/main_menu.xml`/`onOptionsItemSelected`) + `FilterChipsRow` + `LazyColumn` (replacing `EndlessScrollListener`'s near-end-of-list detection with a `snapshotFlow` over `LazyListState.layoutInfo`) + a loading spinner overlay.

**Files:**
- Create: `present-app/src/main/java/com/khosravi/devin/present/present/LogScreen.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.khosravi.devin.present.R
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.log.LogListRowItem
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.SessionStartLogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.present.logitem.DateHeaderRow
import com.khosravi.devin.present.present.logitem.HttpLogRow
import com.khosravi.devin.present.present.logitem.ImageLogRow
import com.khosravi.devin.present.present.logitem.SearchBarRow
import com.khosravi.devin.present.present.logitem.SessionStartRow
import com.khosravi.devin.present.present.logitem.TextLogRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    calendar: CalendarProxy,
    filters: List<FilterItem>,
    selectedFilterId: String?,
    isFilterRowEnabled: Boolean,
    logItems: List<LogListRowItem>,
    isLoading: Boolean,
    onSelectFilter: (FilterItem) -> Unit,
    onFilterAction: (FilterChipAction) -> Unit,
    onSearchTextChange: (String?) -> Unit,
    onTextLogClick: (TextLogItemData) -> Unit,
    onHttpLogClick: (HttpLogItemData) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit,
    onClearFilters: () -> Unit,
    onCreateFilter: () -> Unit,
    onExport: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 3) {
                onLoadMore()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onClearLogs) {
                        Icon(painterResource(R.drawable.baseline_clear_24), contentDescription = stringResource(R.string.menu_clear_logs))
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(painterResource(R.drawable.baseline_refresh_24), contentDescription = stringResource(R.string.menu_refresh))
                    }
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_export_logs)) }, onClick = {
                            overflowOpen = false
                            onExport()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_create_filter)) }, onClick = {
                            overflowOpen = false
                            onCreateFilter()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_clear_filters)) }, onClick = {
                            overflowOpen = false
                            onClearFilters()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_action_toggle_theme)) }, onClick = {
                            overflowOpen = false
                            onToggleTheme()
                        })
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                FilterChipsRow(
                    filters = filters,
                    selectedId = selectedFilterId,
                    enabled = isFilterRowEnabled,
                    onSelect = onSelectFilter,
                    onAction = onFilterAction,
                )
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(logItems) { _, rowItem ->
                        LogListRow(calendar, rowItem, onSearchTextChange, onTextLogClick, onHttpLogClick)
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun LogListRow(
    calendar: CalendarProxy,
    rowItem: LogListRowItem,
    onSearchTextChange: (String?) -> Unit,
    onTextLogClick: (TextLogItemData) -> Unit,
    onHttpLogClick: (HttpLogItemData) -> Unit,
) {
    when (rowItem) {
        is LogListRowItem.Search -> SearchBarRow(text = rowItem.text, hint = rowItem.hint, onTextChange = onSearchTextChange)
        is LogListRowItem.Row -> when (val d = rowItem.data) {
            is DateLogItemData -> DateHeaderRow(calendar, d)
            is TextLogItemData -> TextLogRow(calendar, d, ignoreTagChip = false, onClick = { onTextLogClick(d) })
            is HttpLogItemData -> HttpLogRow(calendar, d, onClick = { onHttpLogClick(d) })
            is ImageLogItemData -> ImageLogRow(calendar, d)
            is SessionStartLogItemData -> SessionStartRow(calendar, d)
            is ReplicatedTextLogItemData -> Unit // never emitted by ReaderViewModel; exhaustiveness guard only
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. `LogScreen` isn't called from anywhere yet, so this only confirms the file itself is valid.

---
### Task 10: Rewrite `LogActivity.kt`

Replaces the FastAdapter/`ActivityLogBinding` glue with Compose state feeding `LogScreen`. `ReaderViewModel` is untouched — this task only changes how `LogActivity` reads its `uiState`/`nextPageFlow` and renders them. The pagination state machine (`loading`/`isFinished`/`currentPage` fields, `resetState()`/`setLoaded()`) is ported from `EndlessScrollListener` into a small private class since `LazyListState` has no RecyclerView-style scroll-listener equivalent. The pin/unpin reorder logic in `onTogglePin` fixes a latent bug in the original `reverseIsPinned` (`if (lastPinnedPosition >= -1)` was always true since `indexOfFirst` returns `-1` at minimum, making the guard a no-op) by only reordering when an unpinned item actually exists.

**Files:**
- Modify: `present-app/src/main/java/com/khosravi/devin/present/present/LogActivity.kt` (full rewrite)

- [ ] **Step 1: Replace the entire file contents**

```kotlin
package com.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.LogListRowItem
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.present.http.HttpLogDetailActivity
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.toUriByFileProvider
import com.khosravi.devin.present.uikit.theme.DevinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class LogActivity : BaseActivity() {

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var calendar: CalendarProxy

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    private val logDetailDialogHost by lazy { LogDetailDialogHost(this, findViewById(android.R.id.content)) }
    private val filterDialogHost by lazy { FilterDialogHost(this, findViewById(android.R.id.content)) }

    private var filterList by mutableStateOf<List<FilterItem>>(emptyList())
    private var selectedFilterId by mutableStateOf<String?>(null)
    private var isFilterRowEnabled by mutableStateOf(true)
    private var logRows by mutableStateOf<List<LogItemData>>(emptyList())
    private var searchRow by mutableStateOf<LogListRowItem.Search?>(null)
    private var isLoading by mutableStateOf(false)

    private val pagination = LogPaginationState()
    private var shareFilterJob: Job? = null
    private var targetTag: String? = null

    private lateinit var importIntentLauncher: ActivityResultLauncher<Intent>
    private val searchInput = MutableSharedFlow<String?>(replay = 0, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        targetTag = intent.getStringExtra(EXTRA_TARGET_TAG)

        importIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onImportFileIntentResult(it)
        }

        setContent {
            DevinTheme {
                LogScreen(
                    calendar = calendar,
                    filters = filterList,
                    selectedFilterId = selectedFilterId,
                    isFilterRowEnabled = isFilterRowEnabled,
                    logItems = buildLogItems(),
                    isLoading = isLoading,
                    onSelectFilter = ::selectNewFilter,
                    onFilterAction = ::onFilterAction,
                    onSearchTextChange = { searchInput.tryEmit(it) },
                    onTextLogClick = { logDetailDialogHost.show(it) },
                    onHttpLogClick = { HttpLogDetailActivity.startActivity(this, it.logId) },
                    onLoadMore = { pagination.maybeLoadMore(::loadMoreItems) },
                    onRefresh = ::refreshLogsAndFilters,
                    onClearLogs = { viewModel.clearLogs() },
                    onClearFilters = { viewModel.clearCustomFilters() },
                    onCreateFilter = ::createFilter,
                    onExport = ::showExportDialog,
                    onToggleTheme = { viewModel.toggleTheme() },
                )
            }
        }

        viewModel.doFirstFetch()

        lifecycleScope.launch {
            viewModel.uiState.collect { result -> onUiStateFlowResult(result) }
        }
        setupNextPageFlow()
        setupSearchFlow()
    }

    private fun buildLogItems(): List<LogListRowItem> {
        val rows = logRows.map { LogListRowItem.Row(it) }
        return searchRow?.let { listOf(it) + rows } ?: rows
    }

    private fun setupNextPageFlow() {
        lifecycleScope.launch {
            viewModel.nextPageFlow.collect {
                pagination.setLoaded(it.pageInfo.isFinished)
                logRows = logRows + it.logs
            }
        }
    }

    private fun setupSearchFlow() {
        lifecycleScope.launch {
            searchInput.debounce(700)
                .distinctUntilChanged()
                .collect { searchText ->
                    searchRow = searchRow?.copy(text = searchText)
                    optCurrentFilterItem()?.let {
                        viewModel.search(it, searchText)
                    }
                }
        }
    }

    private fun onUiStateFlowResult(result: ReaderViewModel.ResultUiState) {
        val presentedFilters = result.filterList?.withNotificationTarget()
        if (presentedFilters != null && !result.updateInfo.skipFilterList) {
            filterList = presentedFilters
        }
        result.logList?.let { logRows = it }
        result.updateInfo.filterIdSelection?.let { selectedFilterId = it }
        isFilterRowEnabled = true
        when (result.updateInfo.callbackId) {
            CALLBACK_ID_REFRESH -> {
                val msgRes = if (result.logList?.isNotEmpty() == true) R.string.msg_refreshed else R.string.msg_empty_filter
                android.widget.Toast.makeText(this, getString(msgRes), android.widget.Toast.LENGTH_SHORT).show()
            }

            CALLBACK_ID_ADD_FILTER -> {
                filterList.lastOrNull()?.let(::selectNewFilter)
            }
        }
        presentedFilters?.selectNotificationTargetIfNeeded()
        pagination.setLoaded(result.pageInfo.isFinished)
    }

    private fun List<FilterItem>.withNotificationTarget(): List<FilterItem> {
        val tag = targetTag ?: return this
        if (any { it is TagFilterItem && it.tagValue == tag }) return this
        return this + TagFilterItem(tag, false)
    }

    private fun List<FilterItem>.selectNotificationTargetIfNeeded() {
        val tag = targetTag ?: return
        targetTag = null
        filterIsInstance<TagFilterItem>()
            .firstOrNull { it.tagValue == tag }
            ?.let(::selectNewFilter)
    }

    private fun loadMoreItems(currentPage: Int) {
        optCurrentFilterItem()?.let {
            viewModel.nextPage(currentPage - 1, it, searchRow?.text)
        }
    }

    private fun selectNewFilter(data: FilterItem) {
        isFilterRowEnabled = false
        pagination.resetState()

        val hint = viewModel.getSearchItemHint(data)
        searchRow = hint?.let { LogListRowItem.Search(hint = it, text = null) }

        lifecycleScope.launch {
            viewModel.newFilterSelected(data).collect()
        }
    }

    private fun resetToDefaultFilter() {
        selectNewFilter(IndexFilterItem.instance)
    }

    private fun onFilterAction(action: FilterChipAction) {
        when (action) {
            is FilterChipAction.TogglePin -> onTogglePin(action.item)
            is FilterChipAction.ShareAsJson -> shareFilterItemLogs(action.item)
            is FilterChipAction.Remove -> removeFilter(action.item)
        }
    }

    private fun onTogglePin(filterItem: FilterItem) {
        lifecycleScope.launch {
            val position = filterList.indexOf(filterItem)
            if (position == -1) return@launch
            val lastUnpinnedPosition = filterList.indexOfFirst { !it.ui.isPinned }
            val resultFlow = if (filterItem.ui.isPinned) viewModel.removeAsPinned(filterItem) else viewModel.markAsPinned(filterItem)
            resultFlow.flowOn(Dispatchers.Main).collect { updated ->
                val newList = filterList.toMutableList()
                newList[position] = updated
                if (lastUnpinnedPosition != -1) {
                    val moved = newList.removeAt(position)
                    val insertAt = (if (position < lastUnpinnedPosition) lastUnpinnedPosition - 1 else lastUnpinnedPosition)
                        .coerceIn(0, newList.size)
                    newList.add(insertAt, moved)
                }
                filterList = newList
            }
        }
    }

    private fun removeFilter(data: CustomFilterItem) {
        val position = filterList.indexOf(data)
        if (position == -1) return
        lifecycleScope.launch {
            val wasSelected = selectedFilterId == data.id
            viewModel.removeFilter(data, position).collect {
                filterList = filterList.toMutableList().apply { removeAt(position) }
                if (wasSelected) {
                    resetToDefaultFilter()
                }
            }
        }
    }

    private fun shareFilterItemLogs(data: TagFilterItem) {
        shareFilterJob?.cancel()
        isLoading = true
        shareFilterJob = viewModel.shareFilterItem(data).flowOn(Dispatchers.Main)
            .onEach { exportFile ->
                isLoading = false
                this.toUriByFileProvider(exportFile).let {
                    val intent = sendOrShareFileIntent(it, MIME_APP_JSON)
                    startActivity(Intent.createChooser(intent, getString(R.string.title_of_share)))
                }
            }.launchIn(lifecycleScope)
    }

    private fun refreshLogsAndFilters() {
        isFilterRowEnabled = false
        optCurrentFilterItem()?.let {
            viewModel.refreshLogsAndFilters(it, callbackId = CALLBACK_ID_REFRESH)
        }
    }

    private fun createFilter() {
        filterDialogHost.show { viewModel.addFilter(it, CALLBACK_ID_ADD_FILTER) }
    }

    private fun showExportDialog() {
        LogExportDialog.newInstance().apply {
            show(supportFragmentManager, LogExportDialog.TAG)
        }
    }

    private fun onImportFileIntentResult(activityResult: ActivityResult) {
        val returnedIntent = activityResult.data
        val uriData = returnedIntent?.data
        if (activityResult.resultCode == RESULT_OK && returnedIntent != null && uriData != null) {
            startActivity(ImportLogActivity.intent(this, uriData))
        }
    }

    private fun optCurrentFilterItem(): FilterItem? = filterList.find { it.id == selectedFilterId }

    private class LogPaginationState {
        private var loading = false
        private var isFinished = false
        private var currentPage = 0

        fun resetState() {
            currentPage = 0
            loading = false
            isFinished = false
        }

        fun setLoaded(isFinished: Boolean) {
            loading = false
            this.isFinished = isFinished
        }

        fun maybeLoadMore(onLoadMore: (page: Int) -> Unit) {
            if (loading || isFinished) return
            loading = true
            currentPage++
            onLoadMore(currentPage)
        }
    }

    companion object {
        const val EXTRA_TARGET_TAG = "targetTag"
        private const val CALLBACK_ID_REFRESH = "refresh"
        private const val CALLBACK_ID_ADD_FILTER = "filter_add"
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: likely **FAIL** on a missing `import com.khosravi.devin.present.R` (used for `R.string.msg_refreshed` etc. via `getString`) and `import com.khosravi.devin.present.log.HttpLogItemData`/`import com.khosravi.devin.present.log.TextLogItemData` unused-import warnings are fine, but a missing `R` import is not. Also check whether `HttpLogItemData`/`TextLogItemData` imports are actually still needed (they're referenced as lambda parameter types passed through only, Kotlin type inference may not need the explicit import if only used positionally — keep the imports regardless, harmless if unused-but-referenced). If the build fails on an actual unresolved reference (not just style), fix it — do not proceed to Task 11 with a broken build.

- [ ] **Step 3: Add the missing `R` import**

Add this import alongside the other `com.khosravi.devin.present.*` imports:

```kotlin
import com.khosravi.devin.present.R
```

- [ ] **Step 4: Re-verify it compiles**

Run:

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---
### Task 11: Delete `LogActivity`-exclusive FastAdapter files

Deletes exactly the files confirmed via full-codebase grep (see spec's corrected "Deleted files" section) to have no callers outside `LogActivity`. `TextLogItem`, `HttpLogItemView`, `ImageLogItem`, `SessionStartLogItem`, `HeaderLogDateItem`, `ReplicatedTextLogItem`/`TextLogSubItem`, their layouts, and `AppExt.toItemViewHolder()` are **not** touched in this task — they're still required by `ImportLogActivity`.

**Files:**
- Delete: `present-app/src/main/java/com/khosravi/devin/present/filter/FilterItemViewHolder.kt`
- Delete: `present-app/src/main/java/com/khosravi/devin/present/present/itemview/SearchItemView.kt`
- Delete: `present-app/src/main/java/com/khosravi/devin/present/uikit/component/EndlessScrollListener.kt`
- Delete: `present-app/src/main/res/layout/activity_log.xml`
- Delete: `present-app/src/main/res/layout/item_search.xml`
- Delete: `present-app/src/main/res/layout/item_filter.xml`
- Delete: `present-app/src/main/res/menu/main_menu.xml`
- Delete: `present-app/src/main/res/menu/menu_filter_item_quick_action.xml`

- [ ] **Step 1: Delete the Kotlin files**

```bash
rm present-app/src/main/java/com/khosravi/devin/present/filter/FilterItemViewHolder.kt
rm present-app/src/main/java/com/khosravi/devin/present/present/itemview/SearchItemView.kt
rm present-app/src/main/java/com/khosravi/devin/present/uikit/component/EndlessScrollListener.kt
```

- [ ] **Step 2: Delete the layout and menu XML files**

```bash
rm present-app/src/main/res/layout/activity_log.xml
rm present-app/src/main/res/layout/item_search.xml
rm present-app/src/main/res/layout/item_filter.xml
rm present-app/src/main/res/menu/main_menu.xml
rm present-app/src/main/res/menu/menu_filter_item_quick_action.xml
```

- [ ] **Step 3: Confirm nothing else references what was deleted**

```bash
grep -rn "FilterItemViewHolder\|SearchItemView\|EndlessScrollListener\|SingleSelectionItemAdapter\|ActivityLogBinding\|ItemSearchBinding\|ItemFilterBinding\|R\.menu\.main_menu\|R\.menu\.menu_filter_item_quick_action\|R\.layout\.activity_log\b\|R\.layout\.item_search\b\|R\.layout\.item_filter\b" present-app/src/main/java
```

Expected: no output. If anything besides `SingleSelectionItemAdapter.kt`'s own class definition (which you are about to also remove usage of, but the class file itself is a separate concern — check Step 4) shows up, stop and investigate before continuing.

- [ ] **Step 4: Check `SingleSelectionItemAdapter.kt` itself**

This class file was not deleted above (only `LogActivity`'s usage of it was removed by the Task 10 rewrite). Confirm it has no other callers before deciding whether to delete it too:

```bash
grep -rln "SingleSelectionItemAdapter" present-app/src/main/java
```

Expected: only `present-app/src/main/java/com/khosravi/devin/present/tool/adapter/SingleSelectionItemAdapter.kt` itself (its own definition). If so, delete it:

```bash
rm present-app/src/main/java/com/khosravi/devin/present/tool/adapter/SingleSelectionItemAdapter.kt
```

- [ ] **Step 5: Confirm the kept-alive files are still referenced by `ImportLogActivity`**

Sanity check that the deletion didn't accidentally take out something `ImportLogActivity` needs:

```bash
grep -n "TextLogItem\|HttpLogItemView\|ReplicatedTextLogItem\|toItemViewHolder" present-app/src/main/java/com/khosravi/devin/present/present/ImportLogActivity.kt
```

Expected: the same 7 matches as before this migration started (imports + usages of `TextLogItem`, `HttpLogItemView`, `ReplicatedTextLogItem`, `toItemViewHolder`) — unchanged, since none of those files were touched.

---

### Task 12: Full build verification

**Files:** none (build check only)

- [ ] **Step 1: Assemble the full debug APK**

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. This is the first check that compiles `ImportLogActivity.kt`, `AppExt.kt`, and every other file in the module together — catching anything Task 10's `compileDebugKotlin` runs (which can succeed per-file-ish while a whole-module assemble still fails on resource linking, e.g. a leftover `R.layout.activity_log` reference somewhere unexpected) missed.

- [ ] **Step 2: Commit point** — full migration compiles end-to-end, `ImportLogActivity` unaffected. Stop here; ask before committing.

---

### Task 13: Manual verification

**Files:** none (manual testing only, per spec's Testing section)

- [ ] **Step 1: Install and launch**

```bash
JAVA_HOME=/Users/nasser/Library/Java/JavaVirtualMachines/corretto-17.0.8.1/Contents/Home ./gradlew :present-app:installDebug
```

Launch the app, get to `LogActivity` with an active client that has a mix of log types. Use `sample-app` to generate text/http/image/session-start logs if the list is empty or missing types.

- [ ] **Step 2: Filter chips**

Confirm: chips scroll horizontally (no 2-row wrap — expected per the spec's simplification), selecting a chip updates the log list and highlights it, the `http` chip (if present) shows the teal okhttp styling instead of the default purple. Long-press a `TagFilterItem` chip: confirm a menu with pin/unpin + share-as-json (no remove). Long-press a custom filter chip (create one via the overflow menu's "Create custom filter" first if none exist): confirm pin/unpin + remove (no share). Confirm the "All" chip has no long-press menu at all.

- [ ] **Step 3: Pin/unpin and remove**

Pin an unpinned filter: confirm it moves next to the other pinned filters and shows the pin icon. Unpin it: confirm it moves back. Remove a custom filter while it's selected: confirm the list falls back to "All".

- [ ] **Step 4: Log list rendering**

Scroll through the list and confirm each row type renders correctly: date header (pill, centered), text log (level icon/badge/color matches level, tag chip visible unless the item is the notification-target auto-selected tag), http log (status/path/domain-in-gray/time), image log (info text, click-to-copy URL toast + clipboard, image renders once loaded), session-start log (colored bar, dot, bold "Session started", version/date chips, right-aligned time).

- [ ] **Step 5: Pagination**

Scroll to the bottom of a filter with more logs than one page. Confirm more logs load automatically near the bottom, and scrolling back up doesn't trigger duplicate loads (watch for repeated/duplicated rows, which would indicate the `loading` guard in `LogPaginationState` isn't working).

- [ ] **Step 6: Search**

Select a filter that supports search (a tag filter, or the http filter). Confirm the search field appears as the first row. Type a query: after ~700ms, confirm the list filters. Switch to a filter that doesn't support search: confirm the search field disappears.

- [ ] **Step 7: Toolbar actions**

Tap refresh: confirm a "refreshed"/"Empty filter" toast appears matching whether logs were found. Tap clear-all-logs: confirm the list empties and resets to "All". Open the overflow menu: confirm export (opens `LogExportDialog`, still XML — should look unchanged), create filter (opens the Compose `FilterDialog`), clear custom filters, and toggle theme all work.

- [ ] **Step 8: Notification tap-through**

If a client has log notifications enabled (see `LogNotificationLaunchCoordinator` in the architecture notes), trigger one and tap it. Confirm `LogActivity` opens with the target tag's filter auto-selected (injecting a synthetic `TagFilterItem` first if it wasn't already in the list).

- [ ] **Step 9: `ImportLogActivity` regression check**

From `LogActivity`'s log detail dialog flow is unaffected, but since several shared classes were kept alive specifically for `ImportLogActivity`, exercise it directly: use the (still-XML) import flow to open a previously exported JSON file in `ImportLogActivity`. Confirm its list still renders text/http log rows correctly and tapping a text log still opens the (Compose) `LogDetailDialog` — this confirms Task 11's deletions didn't regress the unmigrated screen.

- [ ] **Step 10: Commit point** — manual verification passed. Stop here; ask before committing the final state, and before opening a PR (`gh` CLI isn't installed on this machine — use `https://github.com/nasserkhosravi/devin-proj/compare/compose-client-login...compose-log-activity` plus a manual title/body).
