package com.khosravi.devin.present.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupProperties
import com.khosravi.devin.present.R
import com.khosravi.devin.present.uikit.theme.spacing

/** Launches the selected client by deeplink (with an optional JSON extra) and lists its saved launches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(viewModel: LauncherViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val deletedMessage = stringResource(R.string.launcher_deleted)
    val undoLabel = stringResource(R.string.launcher_undo)
    val noHandlerMessage = stringResource(R.string.launcher_failed_no_handler)
    val notExportedMessage = stringResource(R.string.launcher_failed_not_exported)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LauncherEvent.LaunchFailed -> snackbarHostState.showSnackbar(
                    when (event.reason) {
                        LaunchResult.Reason.NoHandler -> noHandlerMessage
                        LaunchResult.Reason.NotExported -> notExportedMessage
                    }
                )
                is LauncherEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.entry)
                }
            }
        }
    }

    Scaffold(
        // Edge-to-edge: the window no longer resizes for the keyboard, so keep the content above it too.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.launcher_title)) },
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
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            item {
                LaunchInputs(
                    state = state,
                    onDeeplinkChange = viewModel::onDeeplinkChange,
                    onKeyChange = viewModel::onKeyChange,
                    onValueChange = viewModel::onValueChange,
                    onCancelEdit = viewModel::cancelEdit,
                    onLaunch = { viewModel.launch(context) },
                )
            }
            if (state.entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.launcher_saved_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(MaterialTheme.spacing.medium),
                    )
                }
            }
            items(state.entries, key = { it.id }) { entry ->
                LaunchEntryCard(
                    entry = entry,
                    isEditing = entry.id == state.editingId,
                    onRelaunch = { viewModel.relaunch(context, entry) },
                    onEdit = { viewModel.edit(entry) },
                    onDelete = { viewModel.delete(entry) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaunchInputs(
    state: LauncherUiState,
    onDeeplinkChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onLaunch: () -> Unit,
) {
    val error = state.inputError
    Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
        if (state.editingId != null) {
            InputChip(
                selected = true,
                onClick = onCancelEdit,
                label = { Text(stringResource(R.string.launcher_editing)) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.launcher_cancel_edit)) },
            )
        }
        DeeplinkField(
            value = state.deeplink,
            suggestions = state.suggestions,
            isError = error == LaunchInputError.InvalidDeeplink,
            onValueChange = onDeeplinkChange,
        )
        OutlinedTextField(
            value = state.extraKey,
            onValueChange = onKeyChange,
            label = { Text(stringResource(R.string.launcher_extra_key)) },
            singleLine = true,
            isError = error == LaunchInputError.MissingKey,
            supportingText = if (error == LaunchInputError.MissingKey) {
                { Text(stringResource(R.string.launcher_error_missing_key)) }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.small),
        )
        OutlinedTextField(
            value = state.extraValue,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.launcher_extra_value)) },
            minLines = 4,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            isError = error == LaunchInputError.InvalidJson,
            supportingText = when (error) {
                LaunchInputError.InvalidJson -> { { Text(stringResource(R.string.launcher_error_invalid_json)) } }
                else -> null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.small),
        )
        Button(
            onClick = onLaunch,
            enabled = state.hasClient && error == null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.medium),
        ) {
            Text(stringResource(R.string.launcher_launch))
        }
    }
}

/** The deeplink field, with the client's declared deeplinks offered below it while it has focus. */
@Composable
private fun DeeplinkField(value: String, suggestions: List<String>, isError: Boolean, onValueChange: (String) -> Unit) {
    var hasFocus by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val matching = remember(value, suggestions) {
        suggestions.filter { it != value && it.contains(value.trim(), ignoreCase = true) }
    }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {
                dismissed = false
                onValueChange(it)
            },
            label = { Text(stringResource(R.string.launcher_deeplink)) },
            singleLine = true,
            isError = isError,
            supportingText = if (isError) {
                { Text(stringResource(R.string.launcher_error_invalid_deeplink)) }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { hasFocus = it.isFocused },
        )
        DropdownMenu(
            expanded = hasFocus && !dismissed && matching.isNotEmpty(),
            onDismissRequest = { dismissed = true },
            // Not focusable, so typing keeps going to the field while suggestions are shown.
            properties = PopupProperties(focusable = false),
        ) {
            matching.take(MAX_SUGGESTIONS).forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        dismissed = true
                        onValueChange(suggestion)
                    },
                )
            }
        }
    }
}

@Composable
private fun LaunchEntryCard(
    entry: LaunchEntry,
    isEditing: Boolean,
    onRelaunch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium),
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
            Text(
                text = entry.input.deeplink,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.input.extraKey?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
                )
            }
            entry.input.extraValue?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onRelaunch) { Text(stringResource(R.string.launcher_relaunch)) }
                TextButton(onClick = onEdit, enabled = !isEditing) { Text(stringResource(R.string.launcher_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.launcher_delete)) }
            }
        }
    }
}

private const val MAX_SUGGESTIONS = 20
