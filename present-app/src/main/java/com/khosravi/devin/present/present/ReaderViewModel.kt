package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.date.CalenderProxy
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.fileForCache
import com.khosravi.devin.present.filter.DefaultFilterItem
import com.khosravi.devin.present.filter.FilterCriteria
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.formatter.JsonFileReporter
import com.khosravi.devin.present.formatter.TextualReport
import com.khosravi.devin.present.formatter.TxtFileReporter
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.toUriByFileProvider
import com.khosravi.devin.write.room.LogTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
            FilterResult(logsWithHeaders)
        }.flowOn(Dispatchers.IO)
    }

    private fun addDateHeadersByDay(logs: List<TextLogItemData>, calendar: CalenderProxy): List<LogItemData> {
        if (logs.isEmpty()) return emptyList()
        val result = ArrayList<LogItemData>()
        var nextDateDifferInDayCode: Int? = null
        logs.forEach {
            val presentDate = calendar.initIfNeed(DatePresent(it.timePresent.timestamp))
            val candidateCode = presentDate.dumbed.hashCode()
            if (candidateCode != nextDateDifferInDayCode) {
                nextDateDifferInDayCode = candidateCode
                result.add(DateLogItemData(presentDate))
            }
            result.add(it)
        }
        return result
    }

    private fun allLogsByCriteria(
        filterItem: FilterItem,
        allLogs: List<LogTable>
    ): List<TextLogItemData> = (filterItem.criteria?.let { criteria ->
        filterByCriteria(allLogs, criteria)
    } ?: allLogs).map { it.toLogItem() }

    private fun filterByCriteria(
        allLogs: List<LogTable>, criteria: FilterCriteria
    ) = allLogs.filter {
        val searchText = criteria.searchText
        val searchTextCondition = if (searchText.isNullOrEmpty()) true
        else it.value.contains(searchText, true)

        val type = criteria.type
        val typeCondition = if (type.isNullOrEmpty()) true else it.type == type

        searchTextCondition && typeCondition
    }

    fun clearLogs() = flow {
        ContentProviderLogsDao.clear(getContext())
        filterRepository.clearSync()
        emit(Unit)
    }.flowOn(Dispatchers.Default)

    fun getLogsInCachedJsonFile(): Flow<Uri> = collectLogs().map {
        JsonFileReporter.create(BuildConfig.VERSION_NAME, it)
    }.map { createCacheShareFile(it) }

    fun getLogsInCachedTxtFile(): Flow<Uri> = collectLogs().map {
        TxtFileReporter.create(BuildConfig.VERSION_NAME, it)
    }.map { createCacheShareFile(it) }

    private fun collectLogs() = flow {
        val result = ContentProviderLogsDao.getAll(getContext()).sortedByDescending { it.date }
        emit(result)
    }

    private fun LogTable.toLogItem(): TextLogItemData {
        return TextLogItemData(value, TimePresent(this.date))
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