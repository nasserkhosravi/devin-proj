package com.khosravi.devin.present.launcher

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khosravi.devin.present.data.CacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LauncherUiState(
    val deeplink: String = "",
    val extraKey: String = "",
    val extraValue: String = "",
    /** The saved entry the next launch updates; null when the next launch is a new entry. */
    val editingId: String? = null,
    val entries: List<LaunchEntry> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val hasClient: Boolean = true,
) {
    val inputError: LaunchInputError? get() = validateLaunchInput(deeplink, extraKey, extraValue)
}

sealed interface LauncherEvent {
    data class LaunchFailed(val reason: LaunchResult.Reason) : LauncherEvent
    data class Deleted(val entry: LaunchEntry) : LauncherEvent
}

/** The Launcher page of the selected client: inputs, saved launches and deeplink suggestions. */
class LauncherViewModel(
    application: Application,
    cacheRepo: CacheRepository,
    private val repository: LaunchRepository,
    private val scanner: DeeplinkScanner,
) : AndroidViewModel(application) {

    private val clientId: String? = cacheRepo.getSelectedClientId()

    private val _state = MutableStateFlow(LauncherUiState(hasClient = clientId != null))
    val state: StateFlow<LauncherUiState> = _state.asStateFlow()

    private val _events = Channel<LauncherEvent>(Channel.BUFFERED)
    val events: Flow<LauncherEvent> = _events.receiveAsFlow()

    init {
        val client = clientId
        if (client != null) {
            viewModelScope.launch {
                val (entries, lastKey) = withContext(Dispatchers.IO) { repository.list(client) to repository.lastExtraKey(client) }
                _state.update { it.copy(entries = entries, extraKey = it.extraKey.ifEmpty { lastKey.orEmpty() }) }
                val suggestions = withContext(Dispatchers.IO) { scanner.scan(client) }
                _state.update { it.copy(suggestions = suggestions) }
            }
        }
    }

    fun onDeeplinkChange(value: String) = _state.update { it.copy(deeplink = value) }
    fun onKeyChange(value: String) = _state.update { it.copy(extraKey = value) }
    fun onValueChange(value: String) = _state.update { it.copy(extraValue = value) }

    /** Launches the current inputs; on success saves them (over the entry being edited, if any). */
    fun launch(context: Context) {
        val current = _state.value
        if (current.inputError != null) return
        val input = LaunchInput.of(current.deeplink, current.extraKey, current.extraValue)
        launchAndRecord(context, input, current.editingId)
    }

    fun relaunch(context: Context, entry: LaunchEntry) = launchAndRecord(context, entry.input, entry.id)

    fun edit(entry: LaunchEntry) = _state.update {
        it.copy(
            deeplink = entry.input.deeplink,
            extraKey = entry.input.extraKey.orEmpty(),
            extraValue = entry.input.extraValue.orEmpty(),
            editingId = entry.id,
        )
    }

    fun cancelEdit() = _state.update { it.copy(editingId = null) }

    fun delete(entry: LaunchEntry) {
        val client = clientId ?: return
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { repository.delete(client, entry.id) } ?: return@launch
            reloadEntries()
            _state.update { if (it.editingId == removed.id) it.copy(editingId = null) else it }
            _events.send(LauncherEvent.Deleted(removed))
        }
    }

    fun undoDelete(entry: LaunchEntry) {
        val client = clientId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.restore(client, entry) }
            reloadEntries()
        }
    }

    private fun launchAndRecord(context: Context, input: LaunchInput, editingId: String?) {
        val client = clientId ?: return
        when (val result = launchClient(context, client, input)) {
            is LaunchResult.Failure -> viewModelScope.launch { _events.send(LauncherEvent.LaunchFailed(result.reason)) }
            LaunchResult.Success -> viewModelScope.launch {
                withContext(Dispatchers.IO) { repository.recordLaunch(client, input, editingId, System.currentTimeMillis()) }
                _state.update { it.copy(editingId = null) }
                reloadEntries()
            }
        }
    }

    private suspend fun reloadEntries() {
        val client = clientId ?: return
        val entries = withContext(Dispatchers.IO) { repository.list(client) }
        _state.update { it.copy(entries = entries) }
    }
}
