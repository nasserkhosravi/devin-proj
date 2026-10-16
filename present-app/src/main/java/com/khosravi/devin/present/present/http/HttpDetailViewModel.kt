package com.khosravi.devin.present.present.http

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khosravi.devin.present.createJsonFileNameForExport
import com.khosravi.devin.present.data.CacheRepository
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.data.LogId
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.requestJsonFileUriToSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HttpDetailViewModel(
    application: Application,
    private val cacheRepo: CacheRepository,
    private val calendarProxy: CalendarProxy,
) : AndroidViewModel(application) {

    private val _detailData = MutableStateFlow<HttpLogDetailData?>(null)
    val detailData: StateFlow<HttpLogDetailData?> = _detailData

    private val _isLoadFailed = MutableStateFlow(false)

    /** True when the fetch finished without a detail (the log is missing or couldn't be parsed). */
    val isLoadFailed: StateFlow<Boolean> = _isLoadFailed

    fun fetchHttpLogDetail(context: Context, logId: LogId) {
        _isLoadFailed.value = false
        viewModelScope.launch {
            flow {
                // No selected client means nothing to show: treat it as a failed load rather than crashing.
                val clientId = cacheRepo.getSelectedClientId()
                val value = clientId?.let { ContentProviderLogsDao.getHttpLog(context, it, logId) }
                emit(value)
            }.flowOn(Dispatchers.Default)
                .collect { result ->
                    _detailData.update { result }
                    _isLoadFailed.update { result == null }
                }
        }
    }

    fun getSelectedClientId() = cacheRepo.getSelectedClientId()

    fun createIntentForSave(): Intent {
        val dateTime = calendarProxy.getFormattedCurrentDateTime()
        val fileName = createJsonFileNameForExport(dateTime)
        return requestJsonFileUriToSave(fileName)
    }

}