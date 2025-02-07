package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.data.UserSettings
import com.khosravi.devin.present.data.CacheRepository
import com.khosravi.devin.present.data.ClientContentProvider
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.ClientLoadedState
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.fileForCache
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterCriteria
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.ImageFilterItem
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.filter.createCriteria
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.formatter.TextualReport
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.toUriByFileProvider
import com.khosravi.devin.write.api.DevinImageFlagsApi
import com.khosravi.devin.write.api.DevinLogFlagsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.IllegalArgumentException

class ReaderViewModel constructor(
    application: Application,
    private val calendar: CalendarProxy,
    private val filterRepository: FilterRepository,
    private val cacheRepo: CacheRepository,
    private val userSettings: UserSettings
) : AndroidViewModel(application) {

    private val _uiStateFlow = MutableStateFlow(ResultUiState(null, null, ResultUiState.UpdateInfo()))
    val uiState = _uiStateFlow.asStateFlow()

    private fun getLogListOfFilter(filterItemId: String): Flow<List<LogItemData>> {
        val filterCriteria = findFilterCriteria(filterItemId)

        return collectLogs().map { logTables ->
            val logs = allLogsByCriteria(filterCriteria, logTables)
            val logsWithHeaders = addDateHeadersByDay(logs, calendar)
            //TODO: temporary disabled, use a setting flag
//            val countedItemsWithHeader = CountingReplicatedTextLogItemDataOperation(logsWithHeaders).get()
            logsWithHeaders
        }.flowOn(Dispatchers.Default)
    }

    fun convertImportedLogsToPresentableLogItems(content: JSONObject): Flow<List<LogItemData>> {
        return flow {
            emit(InterAppJsonConverter.import(content))
        }.map {
            val logsWithHeaders = addDateHeadersByDay(it, calendar)
            //TODO: temporary disabled, use a setting flag
//            CountingReplicatedTextLogItemDataOperation(logsWithHeaders).get()
            logsWithHeaders
        }.flowOn(Dispatchers.Default)
    }

    private fun addDateHeadersByDay(logs: List<LogData>, calendar: CalendarProxy): List<LogItemData> {
        if (logs.isEmpty()) return emptyList()
        val result = ArrayList<LogItemData>()
        var nextDateDifferInDayCode: Int? = null
        logs.forEach {
            val presentDate = calendar.initIfNeed(DatePresent(it.date))
            val candidateCode = presentDate.dumbed.hashCode()
            if (candidateCode != nextDateDifferInDayCode) {
                nextDateDifferInDayCode = candidateCode
                result.add(DateLogItemData(presentDate))
            }
            result.add(TextLogItemData(it.tag, it.value, TimePresent(it.date), getLogIdFromMetaJsonOrDefault(it.meta), it.meta))
        }
        return result
    }

    private fun getLogIdFromMetaJsonOrDefault(meta: String?): Int {
        if (meta.isNullOrEmpty()) return Log.DEBUG
        return JSONObject(meta).optInt(DevinLogFlagsApi.KEY_LOG_LEVEL, Log.DEBUG)
    }

    private fun allLogsByCriteria(
        criteria: FilterCriteria?,
        allLogs: List<LogData>
    ) = (criteria?.let {
        criteria.applyCriteria(allLogs)
    } ?: allLogs)

    fun clearLogs() {
        viewModelScope.launch {
            flow {
                ContentProviderLogsDao.clear(getContext(), clientId = requireSelectedClientId())
                emit(Unit)
            }.flowOn(Dispatchers.Default)
                .collect {
                    getAllFiltersFlow().collect { filterList ->
                        _uiStateFlow.update {
                            ResultUiState(
                                filterList = filterList,
                                logList = emptyList(),
                                ResultUiState.UpdateInfo(filterIdSelection = DEFAULT_FILTER_ID)
                            )
                        }
                    }
                }
        }
    }

    fun clearCustomFilters() {
        viewModelScope.launch {
            flow {
                filterRepository.clearSync()
                emit(Unit)
            }.flowOn(Dispatchers.Default).collect {
                refreshLogsAndFilters(DEFAULT_FILTER_ID, null).collect()
            }
        }
    }

    fun getLogsInJson() = collectLogs().map {
        InterAppJsonConverter.export(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, it)
    }

    fun getLogsInCachedJsonFile(): Flow<Uri> = collectLogs().map {
        InterAppJsonConverter.export(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, it)
    }.map { createCacheShareFile(it) }

    fun getClientList() = flow {
        val result = ClientContentProvider.getClientList(getContext())
        emit(result)
    }.map {
        if (it.isEmpty()) ClientLoadedState.Zero
        else if (it.size == 1) ClientLoadedState.Single(it.first())
        else ClientLoadedState.Multi(it)
    }

    private fun collectLogs() = flow {
        val selectedClientId = getSelectedClientId()
        if (selectedClientId.isNullOrEmpty()) {
            emit(emptyList())
            return@flow
        }
        val result = ContentProviderLogsDao.getAll(getContext(), selectedClientId).sortedByDescending { it.date }
        emit(result)
    }

    fun getDetermineImageLogs() = collectImageLogs().map { list ->
        list.filter { it.status != DevinImageFlagsApi.Status.DOWNLOADING }
            .map { ImageLogItemData(it, DatePresent(it.date), TimePresent(it.date)) }
    }.flowOn(Dispatchers.Default)

    private fun collectImageLogs() = flow {
        val selectedClientId = getSelectedClientId()
        if (selectedClientId.isNullOrEmpty()) {
            emit(emptyList())
            return@flow
        }
        val result = ContentProviderLogsDao.getLogImages(getContext(), selectedClientId)
            .sortedByDescending { it.date }
        emit(result)
    }

    private fun getSelectedClientId() = cacheRepo.getSelectedClientId()

    private fun requireSelectedClientId(): String {
        val clientId = cacheRepo.getSelectedClientId()
        return requireNotNull(clientId)
    }

    fun setSelectedClientId(clientData: ClientData) = cacheRepo.setSelectedClientId(clientData)

    private fun getContext(): Context = getApplication<Application>().applicationContext

    private fun createCacheShareFile(textualReport: TextualReport): Uri {
        val file = getContext().fileForCache(textualReport.fileName)
        file.printWriter().use { out ->
            out.print(textualReport.content)
        }
        return getContext().toUriByFileProvider(file)
    }

    fun addFilter(data: CustomFilterItem, callbackId: String? = null) {
        viewModelScope.launch {
            flow<FilterItem> {
                val result = filterRepository.saveFilter(data)
                if (result.not()) {
                    throw IllegalArgumentException()
                }
                emit(data)
            }.map {
                (_uiStateFlow.value.filterList?.toMutableList() ?: ArrayList()).apply {
                    add(data)
                }
            }.flowOn(Dispatchers.Default)
                .collect { newFilterList ->
                    _uiStateFlow.update {
                        ResultUiState(
                            filterList = newFilterList,
                            logList = it.logList,
                            ResultUiState.UpdateInfo(filterIdSelection = data.id, callbackId = callbackId)
                        )
                    }
                }
        }
    }

    private fun getAllFiltersFlow(): Flow<List<FilterItem>> {
        return flow {
            val result = provideAllFilters()
            emit(result)
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun provideAllFilters(): List<FilterItem> {
        //TODO: maybe its better to make it sequence
        val filterList = ArrayList<FilterItem>().apply {
            //app defined filters
            add(IndexFilterItem())
            add(ImageFilterItem())

            //other filters
            addAll(provideAllComputableFilters())
        }
        return filterList
    }

    private suspend fun provideAllComputableFilters(): List<FilterItem> {
        val userDefinedFilterList = filterRepository.getCustomFilterItemList()
        val result = ArrayList<FilterItem>(userDefinedFilterList)
        if (userSettings.isEnableTagAsFilter) {
            //we consider developer tag as filter
            collectLogs().firstOrNull()?.let {
                val developerTags = filterRepository.createTagFilterList(it, userDefinedFilterList).values
                result.addAll(developerTags)
            }
        }
        return result
    }

    private fun findFilterCriteria(filterItemId: String): FilterCriteria? {
        return _uiStateFlow.value.filterList?.find { it.id == filterItemId }?.let {
            when (it) {
                is CustomFilterItem -> it.criteria
                is TagFilterItem -> it.createCriteria()
                else -> null
            }
        }
    }

    fun refreshOnlyLogs(
        filterItemId: String,
    ): Flow<Unit> {
        return getLogs(filterItemId).map { logList ->
            _uiStateFlow.update {
                ResultUiState(
                    logList = logList, filterList = it.filterList,
                    updateInfo = ResultUiState.UpdateInfo(filterIdSelection = filterItemId, skipFilterList = true)
                )
            }
        }
    }

    fun refreshLogsAndFilters(filterItemId: String, callbackId: String? = null): Flow<Unit> {
        return getLogs(filterItemId).zip(getAllFiltersFlow()) { a, b ->
            Pair(a, b)
        }.map { result ->
            _uiStateFlow.update {
                ResultUiState(
                    filterList = result.second,
                    logList = result.first,
                    ResultUiState.UpdateInfo(
                        filterIdSelection = filterItemId,
                        callbackId = callbackId,
                    )
                )
            }
        }
    }

    private fun getLogs(filterItemId: String) = if (filterItemId == ImageFilterItem.ID) {
        //TODO: its better solve image logs dynamically
        getDetermineImageLogs()
    } else {
        getLogListOfFilter(filterItemId)
    }

    data class ResultUiState(
        val filterList: List<FilterItem>?,
        val logList: List<LogItemData>?,
        val updateInfo: UpdateInfo
    ) {
        class UpdateInfo(
            val filterIdSelection: String? = null,
            val callbackId: String? = null,
            val skipFilterList: Boolean = false
        )
    }

    companion object {
        private const val DEFAULT_FILTER_ID = IndexFilterItem.ID
    }
}