package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.data.LogTable
import com.khosravi.devin.present.date.CalenderProxy
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.fileForCache
import com.khosravi.devin.present.filter.DefaultFilterItem
import com.khosravi.devin.present.filter.FilterCriteria
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.formatter.TextualReport
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.present.logic.CountingReplicatedTextLogItemDataOperation
import com.khosravi.devin.present.toUriByFileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.lang.IllegalArgumentException

class ReaderViewModel constructor(
    application: Application,
    private val filterRepository: FilterRepository,
    private val calendar: CalenderProxy,
) : AndroidViewModel(application) {

    fun getLogListOfFilter(filterItemId: String): Flow<FilterResult> {
        val filterItem = getPresentableFilterList().first { it.id == filterItemId }
        return collectLogs().map { logTables ->
            val logs = allLogsByCriteria(filterItem, logTables)
            val logsWithHeaders = addDateHeadersByDay(logs, calendar)
            val countedItemsWithHeader = CountingReplicatedTextLogItemDataOperation(logsWithHeaders).get()
            FilterResult(countedItemsWithHeader)
        }.flowOn(Dispatchers.IO)
    }

    fun convertImportedLogsToPresentableLogItems(content: JSONObject): Flow<List<LogItemData>> {
        return flow {
            emit(InterAppJsonConverter.import(content))
        }.map {
            val logsWithHeaders = addDateHeadersByDay(it, calendar)
             CountingReplicatedTextLogItemDataOperation(logsWithHeaders).get()
        }.flowOn(Dispatchers.IO)
    }

    private fun addDateHeadersByDay(logs: List<LogTable>, calendar: CalenderProxy): List<LogItemData> {
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
        return JSONObject(meta).optInt("_log_level", Log.DEBUG)
    }

    private fun allLogsByCriteria(
        filterItem: FilterItem,
        allLogs: List<LogTable>
    ) = (filterItem.criteria?.let { criteria ->
        filterByCriteria(allLogs, criteria)
    } ?: allLogs)

    private fun filterByCriteria(
        allLogs: List<LogTable>, criteria: FilterCriteria
    ) = allLogs.filter {
        val searchText = criteria.searchText
        val searchTextConditionResult = if (searchText.isNullOrEmpty()) true
        else it.value.contains(searchText, true)

        val tag = criteria.tag
        val tagConditionResult = if (tag.isNullOrEmpty()) true else it.tag.contains(tag, true)

        searchTextConditionResult && tagConditionResult
    }

    fun clearLogs() = flow {
        ContentProviderLogsDao.clear(getContext())
        filterRepository.clearSync()
        emit(Unit)
    }.flowOn(Dispatchers.Default)

    fun getLogsInJson() = collectLogs().map {
        InterAppJsonConverter.export(BuildConfig.VERSION_NAME, it)
    }

    fun getLogsInCachedJsonFile(): Flow<Uri> = collectLogs().map {
        InterAppJsonConverter.export(BuildConfig.VERSION_NAME, it)
    }.map { createCacheShareFile(it) }

    private fun collectLogs() = flow {
        val result = ContentProviderLogsDao.getAll(getContext()).sortedByDescending { it.date }
        emit(result)
    }

    private fun getContext(): Context = getApplication()

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