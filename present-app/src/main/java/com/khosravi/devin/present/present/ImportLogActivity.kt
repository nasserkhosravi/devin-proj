package com.khosravi.devin.present.present

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.KEY_DATA
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ActivityImportLogBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.getParcelableExtraSupport
import com.khosravi.devin.present.log.ReplicatedTextLogItem
import com.khosravi.devin.present.log.TextLogItem
import com.khosravi.devin.present.readTextAndClose
import com.khosravi.devin.present.toItemViewHolder
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.adapters.GenericItemAdapter
import com.mikepenz.fastadapter.expandable.getExpandableExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

class ImportLogActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private var _binding: ActivityImportLogBinding? = null
    private val binding: ActivityImportLogBinding
        get() = _binding!!

    private val itemAdapter = GenericItemAdapter()
    private val adapter = FastAdapter.with(itemAdapter)
    private var searchTextWatcher: TextWatcher? = null

    @Inject
    lateinit var calendar: CalendarProxy

    @Inject
    lateinit var vmFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityImportLogBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        val content = intent.getParcelableExtraSupport(KEY_DATA, Uri::class.java)?.let { readUriText(it) }
        if (content.isNullOrEmpty()) {
            val errorText =
                if (content == null) getString(R.string.error_msg_something_went_wrong) else getString(R.string.error_msg_empty_file_text)
            Toast.makeText(this, errorText, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.rvMain.adapter = adapter
        adapter.getExpandableExtension()
        adapter.onClickListener = { _, _, item, _ ->
            if (item is TextLogItem) {
                onTextLogItemClick(item)
                true
            } else false
        }

        launch {
            viewModel.convertImportedLogsToPresentableLogItems(JSONObject(content))
                .map { it.toItemViewHolder(calendar) }
                .flowOn(Dispatchers.Main)
                .collect { itemAdapter.set(it) }
        }

        itemAdapter.itemFilter.filterPredicate = { item: GenericItem, constraint: CharSequence? ->
            getTextIfPossible(item)?.contains(constraint.toString(), ignoreCase = true) ?: true
        }
        binding.edSearch.addTextChangedListener { itemAdapter.filter(it.toString()) }
    }

    private fun getTextIfPossible(item: GenericItem): String? {
        return when (item) {
            is ReplicatedTextLogItem -> item.data.text
            is TextLogItem -> item.data.text
            else -> null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchTextWatcher?.let { binding.edSearch.removeTextChangedListener(it) }
        adapter.onClickListener = null
    }

    @SuppressLint("Recycle")
    /**
     * Read text from [android.net.Uri]
     * Added @SuppressLint("Recycle") because [com.khosravi.devin.present.readTextAndClose] close the InputStream
     */
    private fun readUriText(data: Uri): String? {
        //TODO: maybe heavy file, async the reading operation
        val inputStream = contentResolver.openInputStream(data) ?: return null
        return inputStream.readTextAndClose()
    }

    private fun onTextLogItemClick(item: TextLogItem) {
        LogDetailDialog.newInstance(item.data)
            .show(supportFragmentManager, LogDetailDialog.TAG)
    }


    companion object {

        fun intent(context: Context, logUri: Uri) = Intent(context, ImportLogActivity::class.java).apply {
            putExtra(KEY_DATA, logUri)
        }
    }
}