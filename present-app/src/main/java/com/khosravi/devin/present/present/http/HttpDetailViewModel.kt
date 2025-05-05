package com.khosravi.devin.present.present.http

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khosravi.devin.present.data.CacheRepository
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.data.LogId
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
) : AndroidViewModel(application) {

    private val _detailData = MutableStateFlow<HttpLogDetailData?>(null)
    val detailData: StateFlow<HttpLogDetailData?> = _detailData

    fun fetchHttpLogDetail(context: Context, logId: LogId) {
        viewModelScope.launch {
            flow {
                val value = ContentProviderLogsDao.getHttpLog(context, cacheRepo.getSelectedClientId()!!, logId)
                emit(value)
            }.flowOn(Dispatchers.Default)
                .collect { result ->
                    _detailData.update { result }
                }
        }
    }

}