package com.khosravi.devin.present.present

import android.content.Intent
import android.content.pm.PackageManager
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
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ActivityLogBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.filter.DefaultFilterItem
import com.khosravi.devin.present.filter.FilterItemViewHolder
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.filter.ImageFilterItem
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.importFileIntent
import com.khosravi.devin.present.log.TextLogItem
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.setClipboard
import com.khosravi.devin.present.toItemViewHolder
import com.khosravi.devin.present.tool.adapter.SingleSelectionItemAdapter
import com.khosravi.devin.present.tool.adapter.lastIndex
import com.khosravi.devin.present.writeOrSaveFileIntent
import com.khosravi.devin.present.writeTextToUri
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.GenericItemAdapter
import com.mikepenz.fastadapter.expandable.getExpandableExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date
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
            onNewFilterSelected(item.data, index)
            true
        }
        mainAdapter.onClickListener = { _, _, item, _ ->
            when (item) {
                is TextLogItem -> {
                    onTextLogItemClick(item)
                }
            }
            true
        }
        //this call enable being expandable
        mainAdapter.getExpandableExtension()

        doFirstFetch()
    }

    private fun onExportFileIntentResult(activityResult: ActivityResult) {
        val returnedIntent = activityResult.data
        val uriData = returnedIntent?.data
        if (activityResult.resultCode == RESULT_OK && returnedIntent != null && uriData != null) {
            launch {
                viewModel.getLogsInJson()
                    .flowOn(Dispatchers.Main)
                    .map {
                        contentResolver.writeTextToUri(uriData, it.content)
                    }
                    .collect {
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

    private fun doFirstFetch() {
        launch {
            updateFilterList().collect {
                requestRefreshLogItems(IndexFilterItem.ID).collect()
            }
        }
    }

    private fun onNewFilterSelected(data: FilterUiData, index: Int) {
        launch {
            selectNewFilter(data, index).collect()
        }
    }

    private fun selectNewFilter(data: FilterUiData, index: Int): Flow<List<GenericItem>> {
        binding.rvFilter.isEnabled = false
        if (data.id == ImageFilterItem.ID) {
            return viewModel.getImageLogs()
                .map { it.toItemViewHolder(calendar) }
                .flowOn(Dispatchers.Main)
                .onEach {
                    binding.rvFilter.isEnabled = true
                    filterItemAdapter.changeState(index)
                    mainItemAdapter.set(it)
                }
        }
        return viewModel.getLogListOfFilter(data.id).map { it.logList.toItemViewHolder(calendar) }.flowOn(Dispatchers.Main)
            .onEach {
                binding.rvFilter.isEnabled = true
                filterItemAdapter.changeState(index)
                mainItemAdapter.set(it)
            }
    }

    private fun clearLogs() {
        launch {
            viewModel.clearLogs().flowOn(Dispatchers.Main).collect {
                mainItemAdapter.clear()
            }
        }
    }

    private fun clearFilters() {
        launch {
            viewModel.clearFilters().flowOn(Dispatchers.Main).collect {
                val itemCount = filterItemAdapter.adapterItemCount
                if (itemCount > 1) {
                    filterItemAdapter.removeRange(1, itemCount - 1)
                    filterItemAdapter.selectedIndex = 0
                    filterItemAdapter.checkSelection()
                    requestRefreshLogItems(IndexFilterItem.ID).collect()
                }
            }
        }
    }

    private fun updateFilterList(): Flow<Unit> {
        return viewModel.getFlowListPresentableFilter().onEach { list ->
            val filterItems = list.map { FilterItemViewHolder(it.ui) }
            filterItemAdapter.set(filterItems)
            filterItemAdapter.selectedIndex = 0
            filterItemAdapter.checkSelection()
        }.map { }
    }

    private fun requestRefreshLogItems(filterItemId: String): Flow<Unit> {
        return viewModel.getLogListOfFilter(filterItemId).flowOn(Dispatchers.Main).onEach { result ->
            if (result.logList.isEmpty()) {
                Toast.makeText(this@LogActivity, getString(R.string.msg_empty_filter), Toast.LENGTH_SHORT).show()
            } else {
                mainItemAdapter.set(result.logList.toItemViewHolder(calendar))
            }
        }.map { }
    }

    private fun shareJsonFile() {
        launch {
            viewModel.getLogsInCachedJsonFile().map { sendOrShareFileIntent(it, MIME_APP_JSON) }.collect {
                startActivity(Intent.createChooser(it, getString(R.string.title_of_share)))
            }
        }
    }

    private fun exportJsonFile() {
        val intent = writeOrSaveFileIntent("Devin_${Date()}.json", MIME_APP_JSON)
        exportIntentLauncher.launch(Intent.createChooser(intent, getString(R.string.menu_export_json)))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                launch {
                    val filterItemId = filterItemAdapter.optSelectedItem()?.data?.id ?: return@launch
                    requestRefreshLogItems(filterItemId).collect {
                        Toast.makeText(this@LogActivity, getString(R.string.msg_refreshed), Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }

            R.id.action_clear_logs -> {
                clearLogs()
                true
            }

            R.id.action_clear_filters -> {
                clearFilters()
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

    private fun importJsonFile() {
        val chooserIntent = Intent.createChooser(importFileIntent(MIME_APP_JSON), getString(R.string.choosing_intent_title))
        importIntentLauncher.launch(chooserIntent)
    }

    private fun shareJsonInClipboard() {
        launch {
            viewModel.getLogsInJson().collect {
                application.setClipboard(it.content)
                Toast.makeText(this@LogActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createFilter() {
        FilterDialog.newInstance(filterItemAdapter.lastIndex()).apply {
            onConfirm = {
                addFilter(it)
                dismiss()
            }
            show(supportFragmentManager, FilterDialog.TAG)
        }
    }

    private fun addFilter(data: DefaultFilterItem) {
        launch {
            viewModel.addFilter(data)
                .flowOn(Dispatchers.Main)
                .collect {
                    filterItemAdapter.add(FilterItemViewHolder(it.ui))
                    onNewFilterCreated(it.ui, filterItemAdapter.lastIndex())
                }
        }
    }

    private fun onNewFilterCreated(data: FilterUiData, lastIndex: Int) {
        launch {
            selectNewFilter(data, lastIndex).collect()
        }
    }

    private fun onTextLogItemClick(item: TextLogItem) {
        LogDetailDialog.newInstance(item.data)
            .show(supportFragmentManager, LogDetailDialog.TAG)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainAdapter.onClickListener = null
        filterAdapter.onClickListener = null
    }

}