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
import com.khosravi.devin.present.gone
import com.khosravi.devin.present.invisible
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.toUriByFileProvider
import com.khosravi.devin.present.arch.BaseDialog
import com.khosravi.devin.present.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class LogExportDialog : BaseDialog(), CoroutineScope by MainScope() {
    private var _binding: DialogLogExportBinding? = null
    private val binding: DialogLogExportBinding
        get() = _binding!!

    @Inject
    lateinit var vmFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ExportViewModel::class.java]
    }
    private lateinit var saveIntentLauncher: ActivityResultLauncher<Intent>

    private var exportJob: Job? = null
    private var saveTmpFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getAppComponent().inject(this)

        saveIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onSaveFileIntentResult(it)
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
            rdDefault.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    tvDefaultOptionInfo.visible()
                    cvgBuilder.invisible()
                } else {
                    cvgBuilder.visible()
                    tvDefaultOptionInfo.invisible()
                }
            }

            btnSaveLogs.setOnClickListener {
                startExportProcess()
                saveTmpFile?.delete()
                saveTmpFile = null

                exportJob = launch {
                    viewModel.prepareLogsForExport(getCurrentExportOption())
                        .flowOn(Dispatchers.Main)
                        .collect {
                            stopExportProcess()
                            saveTmpFile = it

                            val intent = viewModel.createIntentForSave(needZipFile())
                            saveIntentLauncher.launch(Intent.createChooser(intent, getString(R.string.menu_export_logs)))
                        }
                }
            }

            btnShareLogs.setOnClickListener {
                startExportProcess()
                exportJob = viewModel.prepareLogsForExport(getCurrentExportOption())
                    .flowOn(Dispatchers.Main)
                    .onEach { exportFile ->
                        stopExportProcess()
                        context?.toUriByFileProvider(exportFile)?.let {
                            val intent = sendOrShareFileIntent(it, MIME_APP_JSON)
                            startActivity(Intent.createChooser(intent, getString(R.string.title_of_share)))
                        }
                        dismiss()

                    }
                    .launchIn(this@LogExportDialog)
            }
        }
    }

    private fun onSaveFileIntentResult(activityResult: ActivityResult) {
        val returnedIntent = activityResult.data
        val uriData = returnedIntent?.data
        val context = context ?: return

        if (activityResult.resultCode == RESULT_OK && returnedIntent != null && uriData != null) {
            startExportProcess()
            saveTmpFile?.let {
                exportJob = viewModel.copyFileToUri(uriData, it)
                    .onEach {
                        saveTmpFile?.delete()
                        saveTmpFile = null
                    }
                    .flowOn(Dispatchers.Main)
                    .onEach {
                        stopExportProcess()
                        val msg = if (it) getString(R.string.msg_save_done)
                        else getString(R.string.error_msg_something_went_wrong)
                        Toast.makeText(this@LogExportDialog.context, msg, Toast.LENGTH_LONG).show()
                        dismiss()
                    }.launchIn(this)
            }
        }
    }

    private fun stopExportProcess() {
        _binding?.progressBar?.gone()
    }

    private fun startExportProcess() {
        exportJob?.cancel()
        binding.progressBar.visible()
    }


    private fun getWhitelistText() = binding.edFilterTag.text?.toString()

    private fun needZipFile(): Boolean {
        binding.run {
            if (rdDefault.isChecked) {
                return true
            } else {
                return chSeparateTagFiles.isChecked
            }
        }
    }

    private fun getCurrentExportOption(): ExportOptions = binding.run {
        if (rdDefault.isChecked) {
            viewModel.getExportDefaultOption()
        } else {
            viewModel.buildExportCustom(
                getWhitelistText(),
                edFilterDayNumber.text.toString().toIntOrNull(),
                chSeparateTagFiles.isChecked
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exportJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        exportJob?.cancel()
    }

    companion object {
        const val TAG = "LogExportDialog"
        fun newInstance() = LogExportDialog()
    }
}
