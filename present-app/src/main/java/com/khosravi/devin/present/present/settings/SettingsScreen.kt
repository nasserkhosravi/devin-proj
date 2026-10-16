package com.khosravi.devin.present.present.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.uikit.theme.DevinTheme
import com.khosravi.devin.present.uikit.theme.spacing

/** A single-choice retention setting: pre-formatted [labels] with the current [selectedIndex] (-1 if none). */
data class SettingsChoice(val value: String, val labels: List<String>, val selectedIndex: Int)

private enum class SettingsDialog { NONE, KEEP_DAYS, MAX_COUNT, ADD_TAG }

/**
 * Stateless settings screen. [storageText] is `null` while storage usage is being calculated.
 * Which dialog is open is kept here (saveable, so it survives rotation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    retentionEnabled: Boolean,
    keepDays: SettingsChoice,
    maxCount: SettingsChoice,
    storageText: String?,
    pinnedTags: List<String>,
    onRetentionEnabledChange: (Boolean) -> Unit,
    onKeepDaysPicked: (Int) -> Unit,
    onMaxCountPicked: (Int) -> Unit,
    onUnpinTag: (String) -> Unit,
    onPinTag: (String) -> Unit,
    onBack: () -> Unit,
) {
    var openDialog by rememberSaveable { mutableStateOf(SettingsDialog.NONE) }
    val closeDialog = { openDialog = SettingsDialog.NONE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                            // Icons.AutoMirrored isn't available in this Compose version, so mirror manually.
                            modifier = Modifier.graphicsLayer { if (isRtl) scaleX = -1f }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(stringResource(R.string.settings_section_storage))
            SwitchRow(
                title = stringResource(R.string.settings_retention_enabled),
                checked = retentionEnabled,
                onCheckedChange = onRetentionEnabledChange,
            )
            Summary(stringResource(R.string.settings_retention_enabled_summary))
            ValueRow(
                title = stringResource(R.string.settings_keep_logs_for),
                value = keepDays.value,
                isEnabled = retentionEnabled,
                onClick = { openDialog = SettingsDialog.KEEP_DAYS },
            )
            ValueRow(
                title = stringResource(R.string.settings_max_logs_per_client),
                value = maxCount.value,
                isEnabled = retentionEnabled,
                onClick = { openDialog = SettingsDialog.MAX_COUNT },
            )
            Divider()
            StorageRow(storageText)
            Divider()
            SectionHeader(stringResource(R.string.settings_section_pinned_tags))
            Summary(stringResource(R.string.settings_pinned_tags_summary))
            PinnedTagsSection(
                tags = pinnedTags,
                onUnpin = onUnpinTag,
                onAddClick = { openDialog = SettingsDialog.ADD_TAG },
            )
        }
    }

    when (openDialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.KEEP_DAYS -> RetentionPickerDialog(
            title = stringResource(R.string.settings_keep_logs_for),
            choice = keepDays,
            onPicked = { closeDialog(); onKeepDaysPicked(it) },
            onDismiss = closeDialog,
        )
        SettingsDialog.MAX_COUNT -> RetentionPickerDialog(
            title = stringResource(R.string.settings_max_logs_per_client),
            choice = maxCount,
            onPicked = { closeDialog(); onMaxCountPicked(it) },
            onDismiss = closeDialog,
        )
        SettingsDialog.ADD_TAG -> AddPinnedTagDialog(
            onConfirm = { closeDialog(); onPinTag(it) },
            onDismiss = closeDialog,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.spacing.medium,
                top = MaterialTheme.spacing.medium,
                end = MaterialTheme.spacing.medium,
                bottom = MaterialTheme.spacing.small,
            )
    )
}

@Composable
private fun Summary(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.spacing.medium,
                end = MaterialTheme.spacing.medium,
                bottom = MaterialTheme.spacing.small,
            )
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        // The row handles toggling, so the switch itself isn't separately clickable.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ValueRow(title: String, value: String, isEnabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled, onClick = onClick)
            .alpha(if (isEnabled) 1f else DISABLED_ALPHA)
            .padding(MaterialTheme.spacing.medium)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StorageRow(storageText: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.medium)
    ) {
        Text(text = stringResource(R.string.settings_storage_used), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = storageText ?: stringResource(R.string.settings_storage_calculating),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PinnedTagsSection(tags: List<String>, onUnpin: (String) -> Unit, onAddClick: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.spacing.medium,
                end = MaterialTheme.spacing.medium,
                bottom = MaterialTheme.spacing.medium,
            )
    ) {
        tags.forEach { tag ->
            InputChip(
                selected = false,
                onClick = {},
                label = { Text(tag) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.settings_pinned_tags_remove, tag),
                        modifier = Modifier
                            .size(InputChipDefaults.IconSize)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onUnpin(tag) }
                    )
                },
            )
        }
        AssistChip(
            onClick = onAddClick,
            label = { Text(stringResource(R.string.settings_pinned_tags_add)) },
            leadingIcon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            },
        )
    }
}

@Composable
private fun RetentionPickerDialog(
    title: String,
    choice: SettingsChoice,
    onPicked: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                choice.labels.forEachIndexed { index, label ->
                    val isSelected = index == choice.selectedIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MIN_ROW_HEIGHT)
                            .selectable(selected = isSelected, role = Role.RadioButton) { onPicked(index) }
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = MaterialTheme.spacing.medium)
                        )
                    }
                }
            }
        },
        // Picking a row applies and closes, like setSingleChoiceItems + dismiss; no buttons.
        confirmButton = {},
    )
}

@Composable
private fun AddPinnedTagDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_pinned_tags_add)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.settings_pinned_tags_add_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            // FilterRepository.pinTag trims and ignores blank input, so no validation here.
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private const val DISABLED_ALPHA = 0.38f
private val MIN_ROW_HEIGHT = 48.dp

@Preview
@Composable
private fun SettingsScreenPreview() {
    DevinTheme {
        SettingsScreen(
            retentionEnabled = false,
            keepDays = SettingsChoice("7 days", listOf("1 day", "3 days", "7 days"), 2),
            maxCount = SettingsChoice("10,000", listOf("1,000", "10,000"), 1),
            storageText = null,
            pinnedTags = listOf("okhttp", "crash"),
            onRetentionEnabledChange = {},
            onKeepDaysPicked = {},
            onMaxCountPicked = {},
            onUnpinTag = {},
            onPinTag = {},
            onBack = {},
        )
    }
}
