package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
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
import com.khosravi.devin.present.filter.DefaultFilterItem
import com.khosravi.devin.present.filter.FilterCriteria
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.ImageFilterItem
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.formatter.TextualReport
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.present.logic.CountingReplicatedTextLogItemDataOperation
import com.khosravi.devin.present.toUriByFileProvider
import com.khosravi.devin.write.api.DevinImageFlagsApi
import com.khosravi.devin.write.api.DevinLogFlagsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.lang.IllegalArgumentException

class ReaderViewModel constructor(
    application: Application,
    private val calendar: CalendarProxy,
    private val filterRepository: FilterRepository,
    private val cacheRepo: CacheRepository,
    private val userSettings: UserSettings
) : AndroidViewModel(application) {

    fun getLogListOfFilter(filterItemId: String): Flow<FilterResult> {
        val filterItem = getPresentableFilterList().first { it.id == filterItemId }
        return collectLogs().map { logTables ->
            val logs = allLogsByCriteria(filterItem, logTables)
            val logsWithHeaders = addDateHeadersByDay(logs, calendar)
            //TODO: temporary disabled, use a setting flag
//            val countedItemsWithHeader = CountingReplicatedTextLogItemDataOperation(logsWithHeaders).get()
            FilterResult(logsWithHeaders)
        }.flowOn(Dispatchers.IO)
    }

    fun convertImportedLogsToPresentableLogItems(content: JSONObject): Flow<List<LogItemData>> {
        return flow {
            emit(InterAppJsonConverter.import(content))
        }.map {
            val logsWithHeaders = addDateHeadersByDay(it, calendar)
            //TODO: temporary disabled, use a setting flag
//            CountingReplicatedTextLogItemDataOperation(logsWithHeaders).get()
            logsWithHeaders
        }.flowOn(Dispatchers.IO)
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
        filterItem: FilterItem,
        allLogs: List<LogData>
    ) = (filterItem.criteria?.let { criteria ->
        filterByCriteria(allLogs, criteria)
    } ?: allLogs)

    private fun filterByCriteria(
        allLogs: List<LogData>, criteria: FilterCriteria
    ) = allLogs.filter {
        val searchText = criteria.searchText
        val searchTextConditionResult = if (searchText.isNullOrEmpty()) true
        else it.value.contains(searchText, true)

        val tag = criteria.tag
        val tagConditionResult = if (tag.isNullOrEmpty()) true else it.tag.contains(tag, true)

        searchTextConditionResult && tagConditionResult
    }

    fun clearLogs() = flow {
        ContentProviderLogsDao.clear(getContext(), clientId = requireSelectedClientId())
        emit(Unit)
    }.flowOn(Dispatchers.Default)

    fun clearFilters() = flow {
        filterRepository.clearSync()
        emit(Unit)
    }.flowOn(Dispatchers.Default)

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
    }

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

    fun addFilter(data: DefaultFilterItem) = flow<FilterItem> {
        val result = filterRepository.saveFilter(data)
        if (result.not()) {
            throw IllegalArgumentException()
        }
        emit(data)
    }.flowOn(Dispatchers.Default)

    fun getFlowListPresentableFilter() = flow {
        val filterList = ArrayList<FilterItem>().apply {
            add(IndexFilterItem())
            add(ImageFilterItem())
            addAll(filterRepository.getFilterList())
        }
        emit(filterList)
    }

    private fun getPresentableFilterList() = ArrayList<FilterItem>().apply {
        add(IndexFilterItem())
        addAll(filterRepository.getFilterList())
    }

    class FilterResult(val logList: List<LogItemData>)

}