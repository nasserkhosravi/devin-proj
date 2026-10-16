package com.khosravi.devin.present.present.http

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ShareCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.R
import com.khosravi.devin.present.createCacheShareFile
import com.khosravi.devin.present.createFlowForExportFileIntentResult
import com.khosravi.devin.present.createJsonFileNameForExport
import com.khosravi.devin.present.data.LogId
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.formatter.HttpCurlBuilder
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.formatter.TextualReport
import com.khosravi.devin.present.getLongExtraOrFail
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.setClipboardSafe
import com.khosravi.devin.present.uikit.theme.DevinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Source
import okio.buffer
import javax.inject.Inject


class HttpLogDetailActivity : AppCompatActivity() {

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var calendar: CalendarProxy

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[HttpDetailViewModel::class.java]
    }

    // Registered unconditionally in onCreate so the result is delivered after a recreation too.
    private lateinit var saveIntentLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getAppComponent().inject(this)

        val logId = LogId(intent.getLongExtraOrFail(KEY_LOG_ID))

        saveIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onSaveHarJsonFileIntentResult(it)
        }

        setContent {
            DevinTheme {
                val data by viewModel.detailData.collectAsState()
                val isLoadFailed by viewModel.isLoadFailed.collectAsState()
                HttpLogDetailScreen(
                    data = data,
                    isLoadFailed = isLoadFailed,
                    calendar = calendar,
                    onAction = ::onAction,
                    onCopyText = ::copyTextToClipboard,
                )
            }
        }
        // No-op after a configuration change: the ViewModel keeps the already loaded detail.
        if (viewModel.detailData.value == null) {
            viewModel.fetchHttpLogDetail(this, logId)
        }
    }

    private fun requestFileUriForHarSave(title: String) {
        val intent = viewModel.createIntentForSave()
        saveIntentLauncher.launch(Intent.createChooser(intent, title))
    }

    private fun buildCURL(data: HttpLogDetailData): Flow<String> {
        fun Source.toUtf8Content() =
            this.buffer()
                .use(okio.BufferedSource::readUtf8)

        return flowOf(data).flowOn(Dispatchers.Default).map {
            HttpCurlBuilder.toBuffer(data.harRequest).toUtf8Content()
        }
    }

    private fun buildAndShareCURL(data: HttpLogDetailData) {
        lifecycleScope.launch {
            buildCURL(data).flowOn(Dispatchers.Main).collect { content ->
                val intent = ShareCompat.IntentBuilder(this@HttpLogDetailActivity)
                    .setType("text/plain")
                    .setChooserTitle(getString(R.string.share_curl_command))
                    .setText(content)
                    .createChooserIntent()
                startActivity(intent)
            }
        }
    }

    private fun buildAndCopyCURL(data: HttpLogDetailData) {
        lifecycleScope.launch {
            buildCURL(data).flowOn(Dispatchers.Main).collect { content -> copyTextToClipboard(content) }
        }
    }

    private fun shareHarAsJsonFile(data: HttpLogDetailData) {
        lifecycleScope.launch {
            toSharableContent(data)
                .flowOn(Dispatchers.Main)
                .map {
                    val fileName = createJsonFileNameForExport(calendar.getFormattedCurrentDateTime())
                    val textualReport = TextualReport(fileName, it)
                    createCacheShareFile(textualReport)
                }
                .map { sendOrShareFileIntent(it, MIME_APP_JSON) }
                .collect {
                    startActivity(Intent.createChooser(it, getString(R.string.title_of_share)))
                }
        }
    }

    private fun copyHarToClipboard(data: HttpLogDetailData) {
        lifecycleScope.launch {
            toSharableContent(data)
                .flowOn(Dispatchers.Main)
                .collect { content -> copyTextToClipboard(content) }
        }
    }

    private fun toSharableContent(data: HttpLogDetailData): Flow<String> {
        return flow {
            val harEntryJson = data.getHarEntryAsGsonJsonObject()
            emit(InterAppJsonConverter.exportHARContent(harEntryJson, viewModel.getSelectedClientId()!!))
        }.flowOn(Dispatchers.Default)
    }

    private fun onSaveHarJsonFileIntentResult(activityResult: ActivityResult) {
        val value = viewModel.detailData.value ?: return
        lifecycleScope.launch {
            toSharableContent(value).flatMapConcat {
                createFlowForExportFileIntentResult(it, activityResult)
            }.collect {}
        }

    }

    private fun onAction(action: HttpDetailAction) {
        val detailData = viewModel.detailData.value ?: return
        when (action) {
            HttpDetailAction.ShareHarJson -> shareHarAsJsonFile(detailData)
            HttpDetailAction.ShareCurl -> buildAndShareCURL(detailData)
            HttpDetailAction.CopyHar -> copyHarToClipboard(detailData)
            HttpDetailAction.CopyCurl -> buildAndCopyCURL(detailData)
            HttpDetailAction.CopyResponseContent -> copyBodyToClipboard(detailData.responseBody)
            HttpDetailAction.CopyRequestContent -> copyBodyToClipboard(detailData.requestBody)
            HttpDetailAction.SaveHarJson -> requestFileUriForHarSave(getString(R.string.menu_http_save_har_as_json))
        }
    }

    private fun copyBodyToClipboard(text: String?) {
        if (text == null) {
            Toast.makeText(this, getString(R.string.msg_response_null), Toast.LENGTH_SHORT).show()
        } else {
            copyTextToClipboard(text)
        }
    }

    private fun copyTextToClipboard(text: String) {
        if (setClipboardSafe(text)) {
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val KEY_LOG_ID = "log_id"

        fun startActivity(context: Context, logId: LogId) {
            val intent = Intent(context, HttpLogDetailActivity::class.java).apply {
                putExtra(KEY_LOG_ID, logId.rawId)
            }
            context.startActivity(intent)
        }
    }
}