package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.CacheRepository
import com.khosravi.devin.present.data.ClientContentProvider
import com.khosravi.devin.present.data.ClientLoadedState
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.data.UserSettings
import com.khosravi.devin.present.data.model.GetLogsQueryModel
import com.khosravi.devin.present.data.model.PageInfo
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.filter.setIsPinned
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.optInt
import com.khosravi.devin.read.DevinImageFlagsApi
import com.khosravi.devin.read.DevinLogFlagsApi
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import java.io.File

class ReaderViewModel constructor(
    application: Application,
    private val calendar: CalendarProxy,
    private val filterRepository: FilterRepository,
    private val cacheRepo: CacheRepository,
    private val userSettings: UserSettings
) : AndroidViewModel(application) {
    private var pageInfo = PageInfo()
    private val _uiStateFlow = MutableStateFlow(ResultUiState(null, null, ResultUiState.UpdateInfo(), pageInfo = pageInfo))
    private var lastDayHeaderDate: Int? = null

    val uiState = _uiStateFlow.asStateFlow()
    val nextPageFlow = MutableSharedFlow<ResultNextPage>(replay = 1)

    private fun getLogListOfFilter(model: GetLogsQueryModel): Flow<List<LogItemData>> {
        return collectLogs(model).map { logTables ->
            val logsWithHeaders = addDateHeadersByDay(logTables, calendar)
            logsWithHeaders
        }.flowOn(Dispatchers.Default)
    }

    fun convertImportedLogsToPresentableLogItems(content: String): Flow<List<LogItemData>> {
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
        logs.forEach { data ->
            val presentDate = calendar.initIfNeed(DatePresent(data.date))
            val candidateCode = presentDate.dumbed.hashCode()
            if (candidateCode != nextDateDifferInDayCode) {
                nextDateDifferInDayCode = candidateCode
                if (lastDayHeaderDate != candidateCode) {
                    lastDayHeaderDate = candidateCode
                    result.add(DateLogItemData(presentDate))
                }
            }
            logItemDataFactory(data)?.let { result.add(it) }
        }
        return result
    }

    private fun logItemDataFactory(it: LogData): LogItemData? {
        return when (it.typeId) {
            DevinHttpFlagsApi.TYPE_ID -> {
                ContentProviderLogsDao.mapToHttpModel(it)?.let { HttpLogItemData(it) }
            }

            DevinImageFlagsApi.TYPE_ID -> {
                val imageData = ContentProviderLogsDao.mapToImageModel(it)
                ImageLogItemData(imageData, DatePresent(it.date), TimePresent(it.date))
            }

            else -> TextLogItemData(it.tag, it.value, TimePresent(it.date), getLogIdFromMetaJsonOrDefault(it.meta), it.meta)
        }
    }

    private fun getLogIdFromMetaJsonOrDefault(meta: JsonObject?): Int {
        if (meta == null) return Log.DEBUG
        return meta.optInt(DevinLogFlagsApi.KEY_LOG_LEVEL) ?: Log.DEBUG
    }

    fun clearLogs() {
        val customFilterList = _uiStateFlow.value.filterList?.filterIsInstance<CustomFilterItem>() ?: emptyList()
        viewModelScope.launch {
            flow {
                ContentProviderLogsDao.clear(context = getAppContext(), clientId = getSelectedClientIdOrError())
                emit(Unit)
            }.flowOn(Dispatchers.Default)
                .collect {
                    _uiStateFlow.update {
                        ResultUiState(
                            filterList = ArrayList<FilterItem>(customFilterList).apply {
                                add(0, IndexFilterItem.instance)
                            },
                            logList = emptyList(),
                            updateInfo = ResultUiState.UpdateInfo(filterIdSelection = IndexFilterItem.ID),
                            pageInfo = pageInfo
                        )
                    }
                }
        }
    }

    fun clearCustomFilters() {
        viewModelScope.launch {
            flow {
                filterRepository.clearSync()
                emit(Unit)
            }.flatMapConcat {
                resetPagination()
                val query = buildQueryGet(IndexFilterItem.instance)
                getLogListOfFilter(query)
            }.flowOn(Dispatchers.Default)
                .collect { result ->

                    val newFilterItems = _uiStateFlow.value.filterList?.filter { it !is CustomFilterItem }
                    _uiStateFlow.update {
                        ResultUiState(
                            filterList = newFilterItems,
                            logList = result,
                            ResultUiState.UpdateInfo(filterIdSelection = IndexFilterItem.ID, callbackId = null),
                            pageInfo
                        )
                    }
                }
        }
    }


    fun getClientList() = flow {
        val result = ClientContentProvider.getClientList(getAppContext())
        emit(result)
    }.flowOn(Dispatchers.Default).map {
        if (it.isEmpty()) ClientLoadedState.Zero
        else if (it.size == 1) ClientLoadedState.Single(it.first())
        else ClientLoadedState.Multi(it)
    }

    private fun collectLogs(model: GetLogsQueryModel): Flow<List<LogData>> = flow {
        val result = getClientIdOrReturnEmptyList { clientId ->
            ContentProviderLogsDao.queryLogList(getAppContext(), clientId, model)
        }
        emit(result)
    }

    private fun <T> getClientIdOrReturnEmptyList(action: (clientId: String) -> List<T>): List<T> {
        val clientId = getSelectedClientId()
        if (clientId.isNullOrEmpty()) return emptyList()
        return action(clientId)
    }

    private fun getSelectedClientId() = cacheRepo.getSelectedClientId()

    private fun getSelectedClientIdOrError() = getSelectedClientId()!!

    fun setSelectedClientId(clientData: ClientData) = cacheRepo.setSelectedClientId(clientData)

    private fun getAppContext(): Context = getApplication<Application>().applicationContext

    fun addFilter(data: CustomFilterItem, callbackId: String? = null) {
        viewModelScope.launch {
            flow<FilterItem> {
                val result = filterRepository.saveFilter(data, getSelectedClientIdOrError())
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
                            ResultUiState.UpdateInfo(filterIdSelection = data.id, callbackId = callbackId),
                            pageInfo
                        )
                    }
                }
        }
    }

    private fun getAllFiltersFlow(): Flow<List<FilterItem>> {
        return flow {
            val allComputableFilters = provideAllComputableFilters().sortedByDescending { it.ui.isPinned }
            val result = ArrayList<FilterItem>(allComputableFilters.size + 1).apply {
                add(IndexFilterItem.instance)
                addAll(allComputableFilters)
            }
            emit(result)
        }.flowOn(Dispatchers.Default)
    }

    private fun provideAllComputableFilters(): List<FilterItem> {
        val userDefinedFilterList = filterRepository.getCustomFilterItemList(getSelectedClientIdOrError())

        val result = ArrayList<FilterItem>(userDefinedFilterList)
        if (userSettings.isEnableTagAsFilter) {
            //we consider developer tag as filter
            val tags = ContentProviderLogsDao.getAllTags(getAppContext(), getSelectedClientIdOrError())
            val developerFilters = filterRepository.createTagFilterList(tags, userDefinedFilterList).values.toList()
            result.addAll(developerFilters)

        }
        return result
    }

    fun search(filter: FilterItem, searchText: String?) {
        resetPagination()
        viewModelScope.launch {
            val query = buildQueryGet(filter, pageInfo, searchText)
            getLogListOfFilter(query)
                .collect { logList ->
                    checkPaginationFinish(logList)
                    _uiStateFlow.update {
                        ResultUiState(
                            logList = logList, filterList = it.filterList,
                            pageInfo = pageInfo,
                            updateInfo = ResultUiState.UpdateInfo(filterIdSelection = null, skipFilterList = true)
                        )
                    }
                }
        }
    }

    fun nextPage(currentPageIndex: Int, filter: FilterItem, searchText: String?) {
        if (pageInfo.isFinished) {
            return
        }
        viewModelScope.launch {
            pageInfo = pageInfo.copy(index = currentPageIndex + 1)
            val model = buildQueryGet(filter, pageInfo, searchText)
            getLogListOfFilter(model).collect {
                checkPaginationFinish(it)
                nextPageFlow.tryEmit(ResultNextPage(it, pageInfo))
            }
        }
    }

    fun doFirstFetch() {
        val queryModel = defaultFilterQuery()
        viewModelScope.launch {
            refreshLogsAndFilters(IndexFilterItem.ID, queryModel).collect {}
        }
    }

    fun newFilterSelected(filter: FilterItem): Flow<Unit> {
        resetPagination()
        val model = buildQueryGet(filter)
        return getLogListOfFilter(model).map { logList ->
            checkPaginationFinish(logList)
            _uiStateFlow.update {
                ResultUiState(
                    logList = logList, filterList = it.filterList,
                    pageInfo = pageInfo,
                    updateInfo = ResultUiState.UpdateInfo(filterIdSelection = filter.id, skipFilterList = true)
                )
            }
        }
    }

    fun refreshLogsAndFilters(filter: FilterItem, callbackId: String? = null) {
        lastDayHeaderDate = null
        viewModelScope.launch {
            resetPagination()
            val queryModel = buildQueryGet(filter)
            refreshLogsAndFilters(filter.id, queryModel, callbackId).collect {}
        }
    }

    fun markAsPinned(filterItem: FilterItem): Flow<FilterItem> {
        if (filterItem.ui.isPinned) return flowOf(filterItem)

        return flow {
            filterRepository.saveAsPinned(filterItem)
            val newItem = filterItem.setIsPinned(true)
            emit(newItem)
        }.flowOn(Dispatchers.Default)
    }

    fun removeAsPinned(filterItem: FilterItem): Flow<FilterItem> {
        if (!filterItem.ui.isPinned) return flowOf(filterItem)

        return flow {
            filterRepository.removeAsPinned(filterItem)
            val newItem = filterItem.setIsPinned(isPinned = false)
            emit(newItem)
        }.flowOn(Dispatchers.Default)
    }

    private fun checkPaginationFinish(it: List<LogItemData>) {
        if (it.size < pageInfo.count) {
            pageInfo.isFinished = true
        }
    }

    private fun buildQueryGet(filter: FilterItem) = buildQueryGet(filter, pageInfo, null)

    private fun buildQueryGet(
        filter: FilterItem,
        pageInfo: PageInfo,
        searchText: String?
    ): GetLogsQueryModel {
        val tagOp: DevinUriHelper.OpStringValue?
        val valueOp: DevinUriHelper.OpStringValue?
        var metaParam: DevinUriHelper.OpStringParam? = null

        when (filter) {
            is TagFilterItem -> {
                if (filter.tagValue.contains(DevinHttpFlagsApi.LOG_TAG)) {
                    if (!searchText.isNullOrEmpty()) {
                        metaParam = DevinUriHelper.OpStringParam("url", DevinUriHelper.OpStringValue.Contain(searchText))
                    }
                    tagOp = DevinUriHelper.OpStringValue.EqualTo(filter.id)
                    valueOp = null
                } else {
                    //normal tag id
                    tagOp = DevinUriHelper.OpStringValue.EqualTo(filter.id)
                    valueOp = if (searchText.isNullOrEmpty()) {
                        null
                    } else DevinUriHelper.OpStringValue.Contain(searchText)

                }
            }

            is CustomFilterItem -> {
                tagOp = filter.criteria.tag?.let {
                    DevinUriHelper.OpStringValue.Contain(it)
                }
                valueOp = filter.criteria.searchText?.let {
                    DevinUriHelper.OpStringValue.Contain(it)
                }
            }

            else -> {
                tagOp = null
                valueOp = null
            }
        }
        return GetLogsQueryModel(null, tagOp, valueOp, metaParam = metaParam, page = pageInfo, timeLessThan = null)
    }

    private fun defaultFilterQuery() = buildQueryGet(IndexFilterItem.instance)

    private fun refreshLogsAndFilters(
        filterItemId: String,
        queryModel: GetLogsQueryModel,
        callbackId: String? = null
    ): Flow<Unit> {
        return getLogListOfFilter(queryModel).zip(getAllFiltersFlow()) { a, b ->
            Pair(a, b)
        }.map { result ->
            checkPaginationFinish(result.first)
            _uiStateFlow.update {
                ResultUiState(
                    filterList = result.second,
                    logList = result.first,
                    ResultUiState.UpdateInfo(
                        filterIdSelection = filterItemId,
                        callbackId = callbackId,
                    ),
                    pageInfo
                )
            }
        }
    }

    private fun resetPagination() {
        pageInfo = PageInfo()
    }

    fun getSearchItemHint(filter: FilterItem): String? {
        //see [buildQueryGet] logics
        if (filter is TagFilterItem) {
            return if (filter.tagValue.contains(DevinHttpFlagsApi.LOG_TAG)) {
                "Contain text in url"
            } else {
                "Contain text in log value"
            }
        }
        return null

    }

    fun shareFilterItem(data: TagFilterItem): Flow<File> {
        val exportOptions = ExportViewModel.Common.buildExportOptionsForSingleFilterItemShare(data)
        return ExportViewModel.Common.prepareLogsForExport(getAppContext(), getSelectedClientIdOrError(), calendar, exportOptions)
    }

    fun removeFilter(data: CustomFilterItem, position: Int): Flow<Unit> {
        return flow {
            filterRepository.removeFilter(data)
            _uiStateFlow.value.filterList?.toMutableList()?.also { list ->
                list.removeAt(position)
                _uiStateFlow.update { it.copy(filterList = list) }
            }
            emit(Unit)
        }.flowOn(Dispatchers.IO)
    }

    data class ResultUiState(
        val filterList: List<FilterItem>?,
        val logList: List<LogItemData>?,
        val updateInfo: UpdateInfo,
        val pageInfo: PageInfo
    ) {

        //TODO: events should be triggered by shared flow.
        data class UpdateInfo(
            val filterIdSelection: String? = null,
            val callbackId: String? = null,
            val skipFilterList: Boolean = false
        )
    }


    data class ResultNextPage(
        val logs: List<LogItemData>,
        val pageInfo: PageInfo
    )

    companion object {
        private const val TAG = "ReaderViewModel"
    }
}
