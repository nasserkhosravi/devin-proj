package com.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.DialogLogExportBinding
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.requestJsonFileUriToSave
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.tool.BaseDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class LogExportDialog : BaseDialog(), CoroutineScope by MainScope() {
    private var _binding: DialogLogExportBinding? = null
    private val binding: DialogLogExportBinding
        get() = _binding!!

    @Inject
    lateinit var vmFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }
    private lateinit var exportIntentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.DialogTheme);
        getAppComponent().inject(this)

        exportIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onExportFileIntentResult(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = DialogLogExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.run {
            btnExportLogs.setOnClickListener {
                val intent = requestJsonFileUriToSave()
                exportIntentLauncher.launch(Intent.createChooser(intent, getString(R.string.menu_export_logs)))
            }
            btnShareLogs.setOnClickListener {
                viewModel.shareAllLogs(getWhitelistTags())
                    .map { sendOrShareFileIntent(it, MIME_APP_JSON) }
                    .flowOn(Dispatchers.Main)
                    .onEach {
                        dismiss()
                        startActivity(Intent.createChooser(it, getString(R.string.title_of_share)))
                    }
                    .launchIn(this@LogExportDialog)
            }
        }

    }

    private fun onExportFileIntentResult(activityResult: ActivityResult) {
        val tags = getWhitelistTags()
        val returnedIntent = activityResult.data
        val uriData = returnedIntent?.data
        if (activityResult.resultCode == RESULT_OK && returnedIntent != null && uriData != null) {
            launch {
                viewModel.exportLogsToUri(uriData, tags)
                    .flowOn(Dispatchers.Main)
                    .collect {
                        val msg = if (it) getString(R.string.msg_export_done)
                        else getString(R.string.error_msg_something_went_wrong)
                        Toast.makeText(this@LogExportDialog.context, msg, Toast.LENGTH_LONG).show()
                        dismiss()
                    }
            }
        }
    }


    private fun getWhitelistTags(): List<String> {
        val filterTagRaw = binding.edFilterTag.text?.toString()
        return if (filterTagRaw.isNullOrEmpty()) emptyList()
        else {
            if (filterTagRaw.contains(',')) {
                filterTagRaw.split(',')
            } else {
                listOf(filterTagRaw)
            }
        }
    }

    companion object {
        const val TAG = "LogExportDialog"
        fun newInstance() = LogExportDialog()
    }
}