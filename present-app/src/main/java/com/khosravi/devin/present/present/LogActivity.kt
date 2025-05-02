package com.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ActivityLogBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.filter.FilterItem
import com.khosravi.devin.present.filter.FilterItemViewHolder
import com.khosravi.devin.present.importFileIntent
import com.khosravi.devin.present.log.HttpLogItemView
import com.khosravi.devin.present.log.TextLogItem
import com.khosravi.devin.present.present.http.HttpLogDetailActivity
import com.khosravi.devin.present.present.itemview.SearchItemView
import com.khosravi.devin.present.requestJsonFileUriToSave
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.setClipboardSafe
import com.khosravi.devin.present.toItemViewHolder
import com.khosravi.devin.present.uikit.component.EndlessScrollListener
import com.khosravi.devin.present.tool.adapter.SingleSelectionItemAdapter
import com.khosravi.devin.present.tool.adapter.lastIndex
import com.khosravi.devin.present.writeTextToUri
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.GenericItemAdapter
import com.mikepenz.fastadapter.expandable.getExpandableExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject


class LogActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private val filterItemAdapter = SingleSelectionItemAdapter<FilterItemViewHolder>()
    private val filterAdapter = FastAdapter.with(filterItemAdapter)

    private val mainItemAdapter = GenericItemAdapter()
    private val mainAdapter = FastAdapter.with(mainItemAdapter)

    private var _binding: ActivityLogBinding? = null
    private val binding: ActivityLogBinding
        get() = _binding!!

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var calendar: CalendarProxy

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    private lateinit var importIntentLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportIntentLauncher: ActivityResultLauncher<Intent>
    private var endlessRecyclerOnScrollListener: EndlessScrollListener? = null
    private val searchInput = MutableSharedFlow<String?>(replay = 0, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityLogBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        importIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onImportFileIntentResult(it)
        }
        exportIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onExportFileIntentResult(it)
        }
        binding.rvFilter.adapter = filterAdapter
        binding.rvMain.adapter = mainAdapter
        filterAdapter.onClickListener = { _: View?, _: IAdapter<FilterItemViewHolder>, item: FilterItemViewHolder, index: Int ->
            selectNewFilter(item.data)
            true
        }
        mainAdapter.onClickListener = { _, _, item, _ ->
            when (item) {
                is TextLogItem -> {
                    onTextLogItemClick(item)
                }

                is HttpLogItemView -> {
                    onHttpLogItemClicked(item)
                }
            }
            true
        }
        //this call enable being expandable
        mainAdapter.getExpandableExtension()

        endlessRecyclerOnScrollListener = object : EndlessScrollListener(binding.rvMain.layoutManager as LinearLayoutManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int) {
                loadMoreItems(page)
            }
        }
        binding.rvMain.addOnScrollListener(endlessRecyclerOnScrollListener!!)

        viewModel.doFirstFetch()

        lifecycleScope.launch {
            viewModel.uiState.collect { result ->
                if (_binding == null) {
                    return@collect
                }
                onUiStateFlowResult(result)
            }
        }
        setupNextPageFlow()
        setupSearchFlow()
    }

    private fun setupNextPageFlow() {
        lifecycleScope.launch {
            viewModel.nextPageFlow.collect {
                endlessRecyclerOnScrollListener?.setLoaded(it.pageInfo.isFinished)
                mainItemAdapter.add(it.logs.toItemViewHolder(calendar))
            }

        }
    }

    private fun setupSearchFlow() {
        lifecycleScope.launch {
            searchInput.debounce(700)
                .distinctUntilChanged()
                .collect { searchText ->
                    optCurrentFilterItem()?.let {
                        viewModel.search(it, searchText)
                    }
                }
        }
    }

    private fun onUiStateFlowResult(
        result: ReaderViewModel.ResultUiState,
    ) {
        result.filterList?.let {
            if (!result.updateInfo.skipFilterList) {
                filterItemAdapter.set(it.map { item -> FilterItemViewHolder(item) })
            }
        }
        result.logList?.let {
            val itemCount = mainItemAdapter.adapterItemCount
            if (getSearchItem() != null) {
                mainItemAdapter.removeRange(1, itemCount - 1)
                mainItemAdapter.add(it.toItemViewHolder(calendar))
            } else {
                mainItemAdapter.set(it.toItemViewHolder(calendar))
            }
        }
        getIndexOfFilter(result.updateInfo.filterIdSelection)?.let {
            filterItemAdapter.selectOrCheck(it)
        }
        binding.rvFilter.isEnabled = true
        result.updateInfo.callbackId?.let {
            when (it) {
                CALLBACK_ID_REFRESH -> {
                    if (result.logList?.isNotEmpty() == true) {
                        Toast.makeText(this@LogActivity, getString(R.string.msg_refreshed), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@LogActivity, getString(R.string.msg_empty_filter), Toast.LENGTH_SHORT).show()
                    }
                }

                CALLBACK_ID_ADD_FILTER -> {
                    result.filterList?.let {
                        selectNewFilter(result.filterList.last())
                    }
                }

                else -> {}
            }
        }
        endlessRecyclerOnScrollListener?.setLoaded(result.pageInfo.isFinished)
    }

    private fun loadMoreItems(currentPage: Int) {
        optCurrentFilterItem()?.let {
            viewModel.nextPage(currentPage - 1, it, getSearchItem()?.searchText)
        }
    }

    private fun onHttpLogItemClicked(item: HttpLogItemView) {
        HttpLogDetailActivity.startActivity(this, item.data.logId)
    }

    private fun getIndexOfFilter(id: String?): Int? {
        val index = filterItemAdapter.adapterItems.indexOfFirst { it.data.id == id }
        return if (index == -1) {
            null
        } else {
            index
        }
    }

    private fun onExportFileIntentResult(activityResult: ActivityResult) {
        val returnedIntent = activityResult.data
        val uriData = returnedIntent?.data
        if (activityResult.resultCode == RESULT_OK && returnedIntent != null && uriData != null) {
            launch {
                viewModel.getLogsInJson().map {
                    contentResolver.writeTextToUri(uriData, it.content)
                }.flowOn(Dispatchers.Main).collect {
                    val msg = if (it) getString(R.string.msg_export_done)
                    else getString(R.string.error_msg_something_went_wrong)

                    Toast.makeText(this@LogActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun onImportFileIntentResult(activityResult: ActivityResult) {
        val returnedIntent = activityResult.data
        val uriData = returnedIntent?.data
        if (activityResult.resultCode == RESULT_OK && returnedIntent != null && uriData != null) {
            startActivity(ImportLogActivity.intent(this, uriData))
        }
    }

    private fun selectNewFilter(data: FilterItem) {
        binding.rvFilter.isEnabled = false
        //reset pagination, we are on a new filter
        endlessRecyclerOnScrollListener?.resetState()

        //update search view, we are on a new filter
        val possibleSearchItem = getSearchItem()
        updateSearchItem(data, possibleSearchItem)

        launch {
            viewModel.newFilterSelected(data).collect()
        }
    }

    private fun updateSearchItem(
        filterItem: FilterItem,
        possibleSearchItem: GenericItem?
    ) {
        val searchItemHint = viewModel.getSearchItemHint(filterItem)
        val shouldShowSearch = searchItemHint != null
        if (shouldShowSearch) {
            //should show search
            if (possibleSearchItem != null && possibleSearchItem is SearchItemView) {
                //reset its view
                val searchItem = possibleSearchItem
                searchItem.searchHint = searchItemHint
                searchItem.searchText = null
                mainItemAdapter[0] = searchItem
            } else {
                mainItemAdapter.add(
                    0,
                    SearchItemView(searchHint = searchItemHint, onSearchTextChange = { searchText ->
                        searchInput.tryEmit(searchText)
                    })
                )
            }
        } else {
            mainItemAdapter.adapterItems.firstOrNull()?.let {
                if (it is SearchItemView) {
                    mainItemAdapter.remove(0)
                }
            }
        }
    }


    private fun clearAllLogs() {
        viewModel.clearLogs()
    }

    private fun clearCustomFilters() {
        viewModel.clearCustomFilters()
    }

    private fun shareJsonFile() {
        launch {
            viewModel.getLogsInCachedJsonFile().map { sendOrShareFileIntent(it, MIME_APP_JSON) }
                .flowOn(Dispatchers.Main)
                .collect {
                    startActivity(Intent.createChooser(it, getString(R.string.title_of_share)))
                }
        }
    }

    private fun exportJsonFile() {
        val intent = requestJsonFileUriToSave()
        exportIntentLauncher.launch(Intent.createChooser(intent, getString(R.string.menu_export_json)))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshLogsAndFilters()
                true
            }

            R.id.action_clear_logs -> {
                clearAllLogs()
                true
            }

            R.id.action_clear_filters -> {
                clearCustomFilters()
                true
            }

            R.id.action_import_json -> {
                importJsonFile()
                true
            }

            R.id.action_export_json -> {
                exportJsonFile()
                true
            }

            R.id.action_share_json -> {
                shareJsonFile()
                true
            }

            R.id.action_create_filter -> {
                createFilter()
                true
            }

            R.id.action_export_json_in_clipboard -> {
                shareJsonInClipboard()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshLogsAndFilters() {
        binding.rvFilter.isEnabled = false
        optCurrentFilterItem()?.let {
            viewModel.refreshLogsAndFilters(it, callbackId = CALLBACK_ID_REFRESH)
        }
    }

    private fun importJsonFile() {
        val chooserIntent = Intent.createChooser(importFileIntent(MIME_APP_JSON), getString(R.string.choosing_intent_title))
        importIntentLauncher.launch(chooserIntent)
    }

    private fun shareJsonInClipboard() {
        launch {
            viewModel.getLogsInJson()
                .flowOn(Dispatchers.Main)
                .collect {
                    if (application.setClipboardSafe(it.content)) {
                        Toast.makeText(this@LogActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun createFilter() {
        FilterDialog.newInstance(filterItemAdapter.lastIndex()).apply {
            onConfirm = {
                viewModel.addFilter(it, CALLBACK_ID_ADD_FILTER)
                dismiss()
            }
            show(supportFragmentManager, FilterDialog.TAG)
        }
    }

    private fun onTextLogItemClick(item: TextLogItem) {
        LogDetailDialog.newInstance(item.data).show(supportFragmentManager, LogDetailDialog.TAG)
    }

    private fun optCurrentFilterItem(): FilterItem? = filterItemAdapter.optSelectedItem()?.data

    private fun getSearchItem(): SearchItemView? {
        val possibleSearchItem = mainItemAdapter.adapterItems.firstOrNull()
        if (possibleSearchItem is SearchItemView) return possibleSearchItem
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        endlessRecyclerOnScrollListener?.let { binding.rvMain.removeOnScrollListener(it) }
        mainAdapter.onClickListener = null
        filterAdapter.onClickListener = null
    }

    companion object {
        private const val CALLBACK_ID_REFRESH = "refresh"
        private const val CALLBACK_ID_ADD_FILTER = "filter_add"
        private const val TAG = "LogActivity"
    }

}