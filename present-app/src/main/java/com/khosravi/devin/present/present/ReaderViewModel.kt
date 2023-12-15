package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.fileForCache
import com.khosravi.devin.present.filter.DefaultFilterItem
import com.khosravi.devin.present.filter.FilterCriteria
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.filter.MainFilterItem
import com.khosravi.devin.present.formatter.JsonFileReporter
import com.khosravi.devin.present.formatter.TextualReport
import com.khosravi.devin.present.formatter.TxtFileReporter
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.toUriByFileProvider
import com.khosravi.devin.write.room.LogTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip
import java.lang.IllegalArgumentException

class ReaderViewModel constructor(
    application: Application, private val filterRepository: FilterRepository
) : AndroidViewModel(application) {

    fun getLogListSectioned(): Flow<List<FilterAndLogs>> {
        return collectLogs()
            .zip(getPresentableFilterList(), ::sectionLogs)
            .flowOn(Dispatchers.Default)
    }

    private fun sectionLogs(allLogs: List<LogTable>, filterList: List<FilterItem>): List<FilterAndLogs> {
        return filterList.map { filterItem ->
            val fLogs = allLogsOrByCriteria(filterItem, allLogs)
            FilterAndLogs(filterItem, fLogs)
        }
    }

    private fun allLogsOrByCriteria(
        filterItem: FilterItem,
        allLogs: List<LogTable>
    ) = (filterItem.criteria?.let { criteria ->
        filterByCriteria(allLogs, criteria)
    } ?: allLogs).map { it.toLogItem() }

    private fun filterByCriteria(
        allLogs: List<LogTable>, criteria: FilterCriteria
    ) = allLogs.filter {
        val searchText = criteria.searchText
        val searchTextCondition = if (searchText.isNullOrEmpty()) true
        else it.value.contains(searchText)

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

    /**
     * Get logs and filter them
     */
    fun getLogsByType(data: FilterUiData): Flow<List<LogItemData>> {
        return collectLogs().zip(getPresentableFilterList().map { it.first { it.id == data.id } }) { logs, filterItem ->
            allLogsOrByCriteria(filterItem, logs)
        }.flowOn(Dispatchers.Default)
    }

    private fun collectLogs() = flow {
        val result = ContentProviderLogsDao.getAll(getContext()).sortedByDescending { it.date }
        emit(result)
    }

    private fun LogTable.toLogItem(): LogItemData {
        return LogItemData(value, this.date)
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

    private fun getPresentableFilterList() = flow {
        val filterList = ArrayList<FilterItem>().apply {
            add(MainFilterItem())
            addAll(filterRepository.getFilterList())
        }
        emit(filterList)
    }


    open class FilterAndLogs(
        val filter: FilterItem, val logList: List<LogItemData>
    )

}