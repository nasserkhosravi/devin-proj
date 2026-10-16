package com.khosravi.devin.present.present

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.present.ExportViewModel.ExportEvent
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.uikit.theme.DevinTheme
import com.khosravi.devin.present.uikit.theme.spacing

/**
 * Export dialog content hosted by a navigation `dialog()` destination. Form state is saveable; the export flow
 * (prepare, save picker, copy, share) lives in [viewModel] so it survives configuration changes.
 */
@Composable
fun LogExportRoute(viewModel: ExportViewModel, onClose: () -> Unit) {
    var isBuilder by rememberSaveable { mutableStateOf(false) }
    var tagWhitelist by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("1") }
    var separateTagFiles by rememberSaveable { mutableStateOf(false) }
    val isExporting by viewModel.isExporting.collectAsState()

    val context = LocalContext.current
    val currentOnClose by rememberUpdatedState(onClose)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onSaveLocationPicked(if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportEvent.LaunchSavePicker -> saveLauncher.launch(
                    Intent.createChooser(event.intent, context.getString(R.string.menu_export_logs))
                )

                is ExportEvent.Share -> context.startActivity(
                    Intent.createChooser(
                        sendOrShareFileIntent(event.uri, event.mimeType),
                        context.getString(R.string.title_of_share)
                    )
                )

                is ExportEvent.Message -> Toast.makeText(context, event.textRes, Toast.LENGTH_LONG).show()
                ExportEvent.Close -> currentOnClose()
            }
        }
    }

    fun currentOptions(): ExportOptions =
        if (isBuilder) viewModel.buildExportCustom(tagWhitelist, days.toIntOrNull()?.takeIf { it >= 0 }, separateTagFiles)
        else viewModel.getExportDefaultOption()

    LogExportDialogContent(
        isBuilder = isBuilder,
        onBuilderChange = { isBuilder = it },
        tagWhitelist = tagWhitelist,
        onTagWhitelistChange = { tagWhitelist = it },
        days = days,
        onDaysChange = { days = it.filter(Char::isDigit) },
        separateTagFiles = separateTagFiles,
        onSeparateTagFilesChange = { separateTagFiles = it },
        isExporting = isExporting,
        onSave = { viewModel.startSave(currentOptions()) },
        onShare = { viewModel.startShare(currentOptions()) },
    )
}

/** Stateless export dialog UI: mode selection, builder options, and Save / Share actions. */
@Composable
fun LogExportDialogContent(
    isBuilder: Boolean,
    onBuilderChange: (Boolean) -> Unit,
    tagWhitelist: String,
    onTagWhitelistChange: (String) -> Unit,
    days: String,
    onDaysChange: (String) -> Unit,
    separateTagFiles: Boolean,
    onSeparateTagFilesChange: (Boolean) -> Unit,
    isExporting: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
        Box {
            Column(modifier = Modifier.padding(MaterialTheme.spacing.small)) {
                Row(modifier = Modifier.selectableGroup()) {
                    ModeOption(label = "Default", selected = !isBuilder, onClick = { onBuilderChange(false) })
                    ModeOption(label = "Builder", selected = isBuilder, onClick = { onBuilderChange(true) })
                }

                if (isBuilder) {
                    Column {
                        OutlinedTextField(
                            value = tagWhitelist,
                            onValueChange = onTagWhitelistChange,
                            label = { Text(stringResource(R.string.field_whitelist_tag_filter)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(MaterialTheme.spacing.small))
                        OutlinedTextField(
                            value = days,
                            onValueChange = onDaysChange,
                            label = { Text(stringResource(R.string.field_day_filter)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.toggleable(
                                value = separateTagFiles,
                                onValueChange = onSeparateTagFilesChange,
                                role = Role.Checkbox
                            )
                        ) {
                            Checkbox(checked = separateTagFiles, onCheckedChange = null)
                            Text(
                                text = stringResource(R.string.field_separate_tag_files),
                                modifier = Modifier.padding(MaterialTheme.spacing.small)
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.msg_default_export_explain),
                        modifier = Modifier.padding(MaterialTheme.spacing.small)
                    )
                }

                Button(onClick = onSave, enabled = !isExporting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_save_logs))
                }
                Button(onClick = onShare, enabled = !isExporting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_share_logs))
                }
            }

            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(end = MaterialTheme.spacing.small)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = MaterialTheme.spacing.xs))
    }
}

@Preview
@Composable
private fun LogExportDialogContentPreview() {
    DevinTheme {
        LogExportDialogContent(
            isBuilder = true,
            onBuilderChange = {},
            tagWhitelist = "network,ui",
            onTagWhitelistChange = {},
            days = "1",
            onDaysChange = {},
            separateTagFiles = false,
            onSeparateTagFilesChange = {},
            isExporting = false,
            onSave = {},
            onShare = {},
        )
    }
}
