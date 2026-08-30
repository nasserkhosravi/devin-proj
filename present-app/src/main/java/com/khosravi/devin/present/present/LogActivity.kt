package com.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.R
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.LogListRowItem
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.present.http.HttpLogDetailActivity
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.toUriByFileProvider
import com.khosravi.devin.present.uikit.theme.DevinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class LogActivity : BaseActivity() {

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var calendar: CalendarProxy

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    private val logDetailDialogHost by lazy { LogDetailDialogHost(this, findViewById(android.R.id.content)) }
    private val filterDialogHost by lazy { FilterDialogHost(this, findViewById(android.R.id.content)) }

    private var filterList by mutableStateOf<List<FilterItem>>(emptyList())
    private var selectedFilterId by mutableStateOf<String?>(null)
    private var isFilterRowEnabled by mutableStateOf(true)
    private var logRows by mutableStateOf<List<LogItemData>>(emptyList())
    private var searchRow by mutableStateOf<LogListRowItem.Search?>(null)
    private var isLoading by mutableStateOf(false)
    private var searchSessionId = 0

    private val pagination = LogPaginationState()
    private var shareFilterJob: Job? = null
    private var targetTag: String? = null

    private lateinit var importIntentLauncher: ActivityResultLauncher<Intent>
    private val searchInput = MutableSharedFlow<String?>(replay = 0, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        targetTag = intent.getStringExtra(EXTRA_TARGET_TAG)

        importIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onImportFileIntentResult(it)
        }

        setContent {
            DevinTheme {
                val items = remember(logRows, searchRow) { buildLogItems() }
                LogScreen(
                    calendar = calendar,
                    filters = filterList,
                    selectedFilterId = selectedFilterId,
                    isFilterRowEnabled = isFilterRowEnabled,
                    logItems = items,
                    isLoading = isLoading,
                    onSelectFilter = ::selectNewFilter,
                    onFilterAction = ::onFilterAction,
                    onSearchTextChange = { searchInput.tryEmit(it) },
                    onTextLogClick = { logDetailDialogHost.show(it) },
                    onHttpLogClick = { HttpLogDetailActivity.startActivity(this, it.logId) },
                    onLoadMore = { pagination.maybeLoadMore(::loadMoreItems) },
                    onRefresh = ::refreshLogsAndFilters,
                    onClearLogs = { viewModel.clearLogs() },
                    onClearFilters = { viewModel.clearCustomFilters() },
                    onCreateFilter = ::createFilter,
                    onExport = ::showExportDialog,
                    onToggleTheme = { viewModel.toggleTheme() },
                )
            }
        }

        viewModel.doFirstFetch()

        lifecycleScope.launch {
            viewModel.uiState.collect { result -> onUiStateFlowResult(result) }
        }
        setupNextPageFlow()
        setupSearchFlow()
    }

    private fun buildLogItems(): List<LogListRowItem> {
        val rows = logRows.map { LogListRowItem.Row(it) }
        return searchRow?.let { listOf(it) + rows } ?: rows
    }

    private fun setupNextPageFlow() {
        lifecycleScope.launch {
            viewModel.nextPageFlow.collect {
                pagination.setLoaded(it.pageInfo.isFinished)
                logRows = logRows + it.logs
            }
        }
    }

    private fun setupSearchFlow() {
        lifecycleScope.launch {
            searchInput.debounce(700)
                .distinctUntilChanged()
                .collect { searchText ->
                    searchRow = searchRow?.copy(text = searchText)
                    optCurrentFilterItem()?.let {
                        viewModel.search(it, searchText)
                    }
                }
        }
    }

    private fun onUiStateFlowResult(result: ReaderViewModel.ResultUiState) {
        val presentedFilters = result.filterList?.withNotificationTarget()
        if (presentedFilters != null && !result.updateInfo.skipFilterList) {
            filterList = presentedFilters
        }
        result.logList?.let { logRows = it }
        result.updateInfo.filterIdSelection?.let { selectedFilterId = it }
        isFilterRowEnabled = true
        when (result.updateInfo.callbackId) {
            CALLBACK_ID_REFRESH -> {
                val msgRes = if (result.logList?.isNotEmpty() == true) R.string.msg_refreshed else R.string.msg_empty_filter
                Toast.makeText(this, getString(msgRes), Toast.LENGTH_SHORT).show()
            }

            CALLBACK_ID_ADD_FILTER -> {
                filterList.lastOrNull()?.let(::selectNewFilter)
            }
        }
        presentedFilters?.selectNotificationTargetIfNeeded()
        pagination.setLoaded(result.pageInfo.isFinished)
    }

    private fun List<FilterItem>.withNotificationTarget(): List<FilterItem> {
        val tag = targetTag ?: return this
        if (any { it is TagFilterItem && it.tagValue == tag }) return this
        return this + TagFilterItem(tag, false)
    }

    private fun List<FilterItem>.selectNotificationTargetIfNeeded() {
        val tag = targetTag ?: return
        targetTag = null
        filterIsInstance<TagFilterItem>()
            .firstOrNull { it.tagValue == tag }
            ?.let(::selectNewFilter)
    }

    private fun loadMoreItems(currentPage: Int) {
        optCurrentFilterItem()?.let {
            viewModel.nextPage(currentPage - 1, it, searchRow?.text)
        }
    }

    private fun selectNewFilter(data: FilterItem) {
        isFilterRowEnabled = false
        pagination.resetState()

        val hint = viewModel.getSearchItemHint(data)
        searchRow = hint?.let { LogListRowItem.Search(sessionId = ++searchSessionId, filterId = data.id, hint = it, text = null) }

        lifecycleScope.launch {
            viewModel.newFilterSelected(data).collect()
        }
    }

    private fun resetToDefaultFilter() {
        selectNewFilter(IndexFilterItem.instance)
    }

    private fun onFilterAction(action: FilterChipAction) {
        when (action) {
            is FilterChipAction.TogglePin -> onTogglePin(action.item)
            is FilterChipAction.ShareAsJson -> shareFilterItemLogs(action.item)
            is FilterChipAction.Remove -> removeFilter(action.item)
        }
    }

    private fun onTogglePin(filterItem: FilterItem) {
        lifecycleScope.launch {
            val position = filterList.indexOf(filterItem)
            if (position == -1) return@launch
            val firstUnpinnedIndex = filterList.indexOfFirst { !it.ui.isPinned }
            val resultFlow = if (filterItem.ui.isPinned) viewModel.removeAsPinned(filterItem) else viewModel.markAsPinned(filterItem)
            resultFlow.flowOn(Dispatchers.Main).collect { updated ->
                val newList = filterList.toMutableList()
                newList[position] = updated
                if (firstUnpinnedIndex != -1) {
                    val moved = newList.removeAt(position)
                    val insertAt = (if (position < firstUnpinnedIndex) firstUnpinnedIndex - 1 else firstUnpinnedIndex)
                        .coerceIn(0, newList.size)
                    newList.add(insertAt, moved)
                }
                filterList = newList
            }
        }
    }

    private fun removeFilter(data: CustomFilterItem) {
        val position = filterList.indexOf(data)
        if (position == -1) return
        lifecycleScope.launch {
            val wasSelected = selectedFilterId == data.id
            viewModel.removeFilter(data, position).collect {
                filterList = filterList.toMutableList().apply { removeAt(position) }
                if (wasSelected) {
                    resetToDefaultFilter()
                }
            }
        }
    }

    private fun shareFilterItemLogs(data: TagFilterItem) {
        shareFilterJob?.cancel()
        isLoading = true
        shareFilterJob = viewModel.shareFilterItem(data).flowOn(Dispatchers.Main)
            .onEach { exportFile ->
                isLoading = false
                this.toUriByFileProvider(exportFile).let {
                    val intent = sendOrShareFileIntent(it, MIME_APP_JSON)
                    startActivity(Intent.createChooser(intent, getString(R.string.title_of_share)))
                }
            }.launchIn(lifecycleScope)
    }

    private fun refreshLogsAndFilters() {
        isFilterRowEnabled = false
        optCurrentFilterItem()?.let {
            viewModel.refreshLogsAndFilters(it, callbackId = CALLBACK_ID_REFRESH)
        }
    }

    private fun createFilter() {
        filterDialogHost.show { viewModel.addFilter(it, CALLBACK_ID_ADD_FILTER) }
    }

    private fun showExportDialog() {
        LogExportDialog.newInstance().apply {
            show(supportFragmentManager, LogExportDialog.TAG)
        }
    }

    private fun onImportFileIntentResult(activityResult: ActivityResult) {
        val returnedIntent = activityResult.data
        val uriData = returnedIntent?.data
        if (activityResult.resultCode == RESULT_OK && returnedIntent != null && uriData != null) {
            startActivity(ImportLogActivity.intent(this, uriData))
        }
    }

    private fun optCurrentFilterItem(): FilterItem? = filterList.find { it.id == selectedFilterId }

    private class LogPaginationState {
        private var loading = false
        private var isFinished = false
        private var currentPage = 0

        fun resetState() {
            currentPage = 0
            loading = false
            isFinished = false
        }

        fun setLoaded(isFinished: Boolean) {
            loading = false
            this.isFinished = isFinished
        }

        fun maybeLoadMore(onLoadMore: (page: Int) -> Unit) {
            if (loading || isFinished) return
            loading = true
            currentPage++
            onLoadMore(currentPage)
        }
    }

    companion object {
        const val EXTRA_TARGET_TAG = "targetTag"
        private const val CALLBACK_ID_REFRESH = "refresh"
        private const val CALLBACK_ID_ADD_FILTER = "filter_add"
    }
}
