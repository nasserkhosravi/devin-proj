package ir.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import ir.khosravi.devin.present.BuildConfig
import ir.khosravi.devin.present.creataNotEmpty
import ir.khosravi.devin.present.data.ContentProviderLogsDao
import ir.khosravi.devin.present.data.FilterRepository
import ir.khosravi.devin.present.fileForCache
import ir.khosravi.devin.present.filter.DefaultFilterItem
import ir.khosravi.devin.present.filter.FilterCriteria
import ir.khosravi.devin.present.filter.FilterItem
import ir.khosravi.devin.present.filter.FilterUiData
import ir.khosravi.devin.present.filter.MainFilterItem
import ir.khosravi.devin.present.formatter.JsonFileReporter
import ir.khosravi.devin.present.formatter.TextualReport
import ir.khosravi.devin.present.formatter.TxtFileReporter
import ir.khosravi.devin.present.log.LogItem
import ir.khosravi.devin.present.toUriByFileProvider
import ir.khosravi.devin.write.room.LogTable
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
    fun getLogsByType(data: FilterUiData): Flow<List<LogItem>> {
        return collectLogs().zip(getPresentableFilterList().map { it.first { it.id == data.id } }) { logs, filterItem ->
            allLogsOrByCriteria(filterItem, logs)
        }.flowOn(Dispatchers.Default)
    }

    private fun collectLogs() = flow {
        val result = ContentProviderLogsDao.getAll(getContext()).sortedByDescending { it.date }
        emit(result)
    }

    private fun LogTable.toLogItem(): LogItem {
        return LogItem(value, this.date)
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
        val filterList = getNotCustomFilterList().toMutableList().apply {
            addAll(filterRepository.getFilterList())
        }
        emit(filterList)
    }

    private fun getNotCustomFilterList(): List<FilterItem> {
        return listOf(
            MainFilterItem().apply { ui.isChecked = true },
            DefaultFilterItem(
                FilterUiData(id = KEY_UN_TAG, title = KEY_UN_TAG.creataNotEmpty(), isChecked = false),
                criteria = null
            )
        )
    }


    open class FilterAndLogs(
        val filter: FilterItem, val logList: List<LogItem>
    )

    companion object {
        //value of LoggerImpl.LOG_TYPE_UNTAG
        private const val KEY_UN_TAG = "untag"
    }

}