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
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ActivityLogBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.filter.FilterItemViewHolder
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.importFileIntent
import com.khosravi.devin.present.log.HttpLogItemView
import com.khosravi.devin.present.log.TextLogItem
import com.khosravi.devin.present.present.http.HttpLogDetailActivity
import com.khosravi.devin.present.requestJsonFileUriToSave
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.setClipboard
import com.khosravi.devin.present.toItemViewHolder
import com.khosravi.devin.present.tool.adapter.SingleSelectionItemAdapter
import com.khosravi.devin.present.tool.adapter.lastIndex
import com.khosravi.devin.present.writeTextToUri
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.GenericItemAdapter
import com.mikepenz.fastadapter.expandable.getExpandableExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
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

        doFirstFetch()

        lifecycleScope.launch {
            viewModel.uiState.collect { result ->
                if (_binding == null) {
                    return@collect
                }
                result.filterList?.let {
                    if (!result.updateInfo.skipFilterList) {
                        filterItemAdapter.set(it.map { FilterItemViewHolder(it.ui) })
                    }
                }
                result.logList?.let {
                    mainItemAdapter.set(it.toItemViewHolder(calendar))
                }
                getIndexOfFilter(result.updateInfo.filterIdSelection)?.let {
                    filterItemAdapter.selectOrCheck(it)
                }
                binding.rvFilter.isEnabled = true
                result.updateInfo.callbackId?.let {
                    if (it == CALLBACK_ID_REFRESH) {
                        if (result.logList?.isNotEmpty() == true) {
                            Toast.makeText(this@LogActivity, getString(R.string.msg_refreshed), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@LogActivity, getString(R.string.msg_empty_filter), Toast.LENGTH_SHORT).show()
                        }
                    } else if (it == CALLBACK_ID_ADD_FILTER) {
                        result.filterList?.let {
                            selectNewFilter(result.filterList.last().ui)
                        }
                    }
                }
            }
        }
    }

    private fun onHttpLogItemClicked(item: HttpLogItemView) {
        HttpLogDetailActivity.startActivity(this,item.data.logId)
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
                viewModel.getLogsInJson().flowOn(Dispatchers.Main).map {
                    contentResolver.writeTextToUri(uriData, it.content)
                }.collect {
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
            viewModel.refreshLogsAndFilters(IndexFilterItem.ID).collect()
        }
    }

    private fun selectNewFilter(data: FilterUiData) {
        binding.rvFilter.isEnabled = false
        launch {
            viewModel.refreshOnlyLogs(data.id).collect()
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
            viewModel.getLogsInCachedJsonFile().map { sendOrShareFileIntent(it, MIME_APP_JSON) }.collect {
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
                val filterItemId = filterItemAdapter.optSelectedItem()?.data?.id ?: return true
                refreshLogsAndFilters(filterItemId)
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

    private fun refreshLogsAndFilters(filterItemId: String) {
        binding.rvFilter.isEnabled = false
        launch {
            viewModel.refreshLogsAndFilters(filterItemId, callbackId = CALLBACK_ID_REFRESH).collect()
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
                viewModel.addFilter(it, CALLBACK_ID_ADD_FILTER)
                dismiss()
            }
            show(supportFragmentManager, FilterDialog.TAG)
        }
    }

    private fun onTextLogItemClick(item: TextLogItem) {
        LogDetailDialog.newInstance(item.data).show(supportFragmentManager, LogDetailDialog.TAG)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        mainAdapter.onClickListener = null
        filterAdapter.onClickListener = null
    }

    companion object {
        private const val CALLBACK_ID_REFRESH = "refresh"
        private const val CALLBACK_ID_ADD_FILTER = "filter_add"
    }

}