package ir.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import ir.khosravi.devin.present.data.ContentProviderLogsDao
import ir.khosravi.devin.present.fileForCache
import ir.khosravi.devin.present.filter.DefaultFilterItem
import ir.khosravi.devin.present.filter.FilterItem
import ir.khosravi.devin.present.filter.MainFilterItem
import ir.khosravi.devin.present.formatter.TxtFileFormatter
import ir.khosravi.devin.present.log.LogItem
import ir.khosravi.devin.present.toUriByFileProvider
import ir.khosravi.devin.write.room.LogTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ReaderViewModel constructor(
    application: Application,
) : AndroidViewModel(application) {

    fun getLogListSectioned(): Flow<List<FilterAndLogs>> {
        return collectLogs().map {
            sectionLogs(it)
        }.flowOn(Dispatchers.Default)
    }

    private fun sectionLogs(logs: List<LogTable>) = logs.groupBy { it.type }.map { grouped ->
        FilterAndLogs(
            DefaultFilterItem(grouped.key),
            grouped.value.map { it.toLogItem() })
    }

    fun createAndAddMainItemToFirstIndex(data: List<FilterAndLogs>): Flow<List<FilterAndLogs>> {
        return flow {
            val mainLogs = data.map { it.logList }.flatten()
            val mainItem = FilterAndLogs(filter = MainFilterItem(), mainLogs)
            emit(ArrayList(data).apply {
                add(0, mainItem)
            })
        }
    }

    fun clearLogs() = flow {
        ContentProviderLogsDao.clear(getContext())
        emit(Unit)
    }.flowOn(Dispatchers.Default)

    fun getLogsInCachedTxtFile(): Flow<Uri> = collectLogs().map {
        TxtFileFormatter.execute(getContext(), it)
    }.map { target ->
        val file = getContext().fileForCache()
        file.printWriter().use { out ->
            out.print(target)
        }
        getContext().toUriByFileProvider(file)
    }

    /**
     * Get logs and filter them by [type], If type is null or empty then it means all logs.
     */
    fun getLogsByType(type: String?): Flow<List<LogItem>> {
        return collectLogs().map {
            if (type.isNullOrEmpty()) {
                it
            } else it.filter { it.type == type }
        }.map { it -> it.map { it.toLogItem() } }
            .flowOn(Dispatchers.Default)
    }

    private fun collectLogs() = flow {
        val result = ContentProviderLogsDao.getAll(getContext())
            .sortedByDescending { it.date }
        emit(result)
    }

    private fun LogTable.toLogItem(): LogItem {
        return LogItem(value,this.date)
    }

    private fun getContext(): Context = getApplication()

    open class FilterAndLogs(
        val filter: FilterItem,
        val logList: List<LogItem>
    )

}