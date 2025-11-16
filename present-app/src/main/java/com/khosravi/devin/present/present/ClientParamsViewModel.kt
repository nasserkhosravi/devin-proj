package com.khosravi.devin.present.present

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.content.edit

class ClientParamsViewModel @Inject constructor(
    appContext: Context,
) : ViewModel() {

    private val prefs: SharedPreferences = appContext.getSharedPreferences("client_params", Context.MODE_PRIVATE)

    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus = _saveStatus.asStateFlow()

    fun loadParams(clientId: String) {
        viewModelScope.launch {
            _text.value = prefs.getString(clientId, "") ?: ""
        }
    }

    fun saveParams(clientId: String, content: String) {
        viewModelScope.launch {
            try {
                prefs.edit { putString(clientId, content) }
                _saveStatus.value = SaveStatus.Success
            } catch (e: Exception) {
                // In a real app, you might want to log this error
                _saveStatus.value = SaveStatus.Error(e.message ?: "Failed to save")
            }
        }
    }

    fun resetSaveStatus() {
        _saveStatus.value = SaveStatus.Idle
    }

    sealed class SaveStatus {
        object Idle : SaveStatus()
        object Success : SaveStatus()
        data class Error(val message: String) : SaveStatus()
    }
}