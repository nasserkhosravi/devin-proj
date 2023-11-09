package ir.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import ir.khosravi.devin.present.R
import ir.khosravi.devin.present.databinding.ActivityLogBinding
import ir.khosravi.devin.present.di.ViewModelFactory
import ir.khosravi.devin.present.di.getAppComponent
import ir.khosravi.devin.present.filter.FilterAdapter
import ir.khosravi.devin.present.filter.FilterItem
import ir.khosravi.devin.present.log.LogAdapter
import ir.khosravi.devin.present.shareFileIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject


class LogActivity : AppCompatActivity(), FilterAdapter.Listener, CoroutineScope by MainScope() {

    private val logAdapter = LogAdapter()
    private val filterAdapter = FilterAdapter(this)

    private var _binding: ActivityLogBinding? = null
    private val binding: ActivityLogBinding
        get() = _binding!!

    @Inject
    lateinit var vmFactory: ViewModelFactory

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
        binding.rvLog.adapter = logAdapter

        launch {
            requestRefresh(0)
                .collect()
        }
    }

    override fun onNewFilterSelected(data: FilterItem, newIndex: Int) {
        launch {
            binding.rvFilter.isEnabled = false
            viewModel.getLogsByType(data.id)
                .flowOn(Dispatchers.Main)
                .collect {
                    binding.rvFilter.isEnabled = true
                    logAdapter.replaceAll(it)
                }
        }
    }

    private fun onClearLogs() {
        launch {
            viewModel.clearLogs()
                .collect {
                    Toast.makeText(this@LogActivity, getString(R.string.msg_logs_cleared), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun requestRefresh(index: Int): Flow<List<ReaderViewModel.FilterAndLogs>> {
        return viewModel.getLogListSectioned()
            .flatMapLatest { viewModel.createAndAddMainItemToFirstIndex(it) }
            .flowOn(Dispatchers.Main)
            .onEach {
                filterAdapter.replaceAll(it.map { it.filter })
                if (it[index].logList.isEmpty()) {
                    Toast.makeText(this@LogActivity, getString(R.string.msg_empty_filter), Toast.LENGTH_SHORT).show()
                } else {
                    logAdapter.replaceAll(it[index].logList)
                }
            }
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
                    requestRefresh(filterAdapter.selectedIndex).collect {
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

            else -> super.onOptionsItemSelected(item)
        }
    }

}