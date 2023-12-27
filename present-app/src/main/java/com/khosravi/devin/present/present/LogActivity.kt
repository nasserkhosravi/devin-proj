package com.khosravi.devin.present.present

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.ContentProviderLogsDao.PERMISSION_READ
import com.khosravi.devin.present.data.ContentProviderLogsDao.PERMISSION_WRITE
import com.khosravi.devin.present.databinding.ActivityLogBinding
import com.khosravi.devin.present.date.CalenderProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.filter.DefaultFilterItem
import com.khosravi.devin.present.filter.FilterItemViewHolder
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.filter.IndexFilterItem
import com.khosravi.devin.present.log.ReplicatedTextLogItem
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.HeaderLogDateItem
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.TextLogSubItem
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.log.TextLogItem
import com.khosravi.devin.present.shareFileIntent
import com.khosravi.devin.present.tool.adapter.SingleSelectionItemAdapter
import com.khosravi.devin.present.tool.adapter.lastIndex
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
    lateinit var calendar: CalenderProxy

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityLogBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.rvFilter.adapter = filterAdapter
        binding.rvMain.adapter = mainAdapter
        filterAdapter.onClickListener = { _: View?, _: IAdapter<FilterItemViewHolder>, item: FilterItemViewHolder, index: Int ->
            onNewFilterSelected(item.data, index)
            true
        }
        mainAdapter.onClickListener = { _, _, item, position ->
            when (item) {
                is ReplicatedTextLogItem -> {
                    onReplicatedTextLogItemClick(item)
                }

                is TextLogSubItem -> {
                    onChildReplicatedTextLogItemClick(item)
                }
            }
            true
        }
        //this call enable being expandable
        mainAdapter.getExpandableExtension()

        if (
            ContextCompat.checkSelfPermission(this, PERMISSION_READ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, PERMISSION_WRITE) != PackageManager.PERMISSION_GRANTED
        ) {
            launchPermissionGranting()
            return
        }
        doFirstFetch()
    }

    private fun launchPermissionGranting() {
        val permissionLauncher: ActivityResultLauncher<Array<String>> =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                if (result.all { it.value }) {
                    doFirstFetch()
                } else {
                    Toast.makeText(this, "permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        permissionLauncher.launch(arrayOf(PERMISSION_READ, PERMISSION_WRITE))
    }

    private fun doFirstFetch() {
        launch {
            updateFilterList().collect {
                requestRefreshLogItems(IndexFilterItem.ID)
                    .collect()
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
        return viewModel.getLogListOfFilter(data.id)
            .map { it.logList.map { it.toItemViewHolder() } }
            .flowOn(Dispatchers.Main)
            .onEach {
                binding.rvFilter.isEnabled = true
                filterItemAdapter.changeState(index)
                mainItemAdapter.set(it)
            }
    }

    private fun onClearLogs() {
        launch {
            viewModel.clearLogs()
                .flowOn(Dispatchers.Main)
                .collect {
                    filterItemAdapter.clear()
                    mainItemAdapter.clear()
                    Toast.makeText(this@LogActivity, getString(R.string.msg_cleared), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateFilterList(): Flow<Unit> {
        return viewModel.getFlowListPresentableFilter()
            .onEach { list ->
                val filterItems = list.map { FilterItemViewHolder(it.ui) }
                filterItemAdapter.set(filterItems)
                filterItemAdapter.selectedIndex = 0
                filterItemAdapter.checkSelection()
            }.map { }
    }

    private fun requestRefreshLogItems(filterItemId: String): Flow<Unit> {
        return viewModel.getLogListOfFilter(filterItemId)
            .flowOn(Dispatchers.Main)
            .onEach { result ->
                if (result.logList.isEmpty()) {
                    Toast.makeText(this@LogActivity, getString(R.string.msg_empty_filter), Toast.LENGTH_SHORT).show()
                } else {
                    mainItemAdapter.set(result.logList.map { it.toItemViewHolder() })
                }
            }.map { }
    }

    private fun shareTxtFile() {
        launch {
            viewModel.getLogsInCachedTxtFile().map { shareFileIntent(it, "text/plain") }
                .collect {
                    startActivity(Intent.createChooser(it, getString(R.string.title_of_share)))
                }
        }
    }

    private fun shareJsonFile() {
        launch {
            viewModel.getLogsInCachedJsonFile().map { shareFileIntent(it, "application/json") }
                .collect {
                    startActivity(Intent.createChooser(it, getString(R.string.title_of_share)))
                }
        }
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

            R.id.action_clear -> {
                onClearLogs()
                true
            }

            R.id.action_share_txt -> {
                shareTxtFile()
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

            else -> super.onOptionsItemSelected(item)
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
            selectNewFilter(data, lastIndex)
                .collect()
        }
    }

    private fun LogItemData.toItemViewHolder(): GenericItem {
        return when (this) {
            is DateLogItemData -> HeaderLogDateItem(calendar, this)
            is TextLogItemData -> TextLogItem(calendar, this)
            is ReplicatedTextLogItemData -> ReplicatedTextLogItem(calendar, this).apply {
                subItems = data.list.map { TextLogSubItem(calendar, it, this) }.toMutableList()
                onItemClickListener = { _,_, item, position ->
//                    onChildReplicatedTextLogItemClick(item,position)
                    true
                }
            }
        }
    }

    private fun onReplicatedTextLogItemClick(item: ReplicatedTextLogItem) {
//        item.isExpanded = !item.isExpanded
    }

    private fun onChildReplicatedTextLogItemClick(item: TextLogSubItem) {

    }

    override fun onDestroy() {
        super.onDestroy()
        mainAdapter.onClickListener = null
        filterAdapter.onClickListener = null
    }

}