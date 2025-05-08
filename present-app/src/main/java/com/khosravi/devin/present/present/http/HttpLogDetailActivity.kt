package com.khosravi.devin.present.present.http

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayoutMediator
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.R
import com.khosravi.devin.present.createCacheShareFile
import com.khosravi.devin.present.createFlowForExportFileIntentResult
import com.khosravi.devin.present.data.LogId
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.databinding.ActivityHttpLogDetailBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.formatter.HttpCurlBuilder
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.formatter.TextualReport
import com.khosravi.devin.present.getLongExtraOrFail
import com.khosravi.devin.present.present.http.items.HttpDetailContentItemView
import com.khosravi.devin.present.present.http.items.HttpDetailOverviewItemView
import com.khosravi.devin.present.requestJsonFileUriToSave
import com.khosravi.devin.present.sendOrShareFileIntent
import com.khosravi.devin.present.setClipboardSafe
import com.khosravi.devin.present.tool.adapter.isEmpty
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.GenericItemAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
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


class HttpLogDetailActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private var _binding: ActivityHttpLogDetailBinding? = null
    private val binding: ActivityHttpLogDetailBinding
        get() = _binding!!

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var calendar: CalendarProxy

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[HttpDetailViewModel::class.java]
    }
    private lateinit var exportIntentLauncher: ActivityResultLauncher<Intent>


    private val itemAdapter = GenericItemAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getAppComponent().inject(this)
        _binding = ActivityHttpLogDetailBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        val logId = LogId(intent.getLongExtraOrFail(KEY_LOG_ID))

        exportIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            onExportHarJsonFileIntentResult(it)
        }

        launch {
            viewModel.detailData.collect {
                if (it != null) {
                    showResult(it)
                }
            }
        }
        viewModel.fetchHttpLogDetail(this, logId)

        val adapter = FastAdapter.with(itemAdapter)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = getString(R.string.overview)
                1 -> tab.text = getString(R.string.request)
                2 -> tab.text = getString(R.string.response)
            }
        }.attach()

        binding.ivIcMenu.setOnClickListener { v ->
            val popup = PopupMenu(this, v)
            popup.menuInflater.inflate(R.menu.http_detail_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                onOptionsItemSelected(item)
            }
            popup.show()
        }
        binding.ivIcMenu.setBackgroundColor(window.statusBarColor)

    }

    private fun requestFileUriForHarExport(title: String) {
        val intent = requestJsonFileUriToSave()
        exportIntentLauncher.launch(Intent.createChooser(intent, title))
    }

    private fun showResult(detail: HttpLogDetailData) {
        if (itemAdapter.isEmpty()) {
            val jsonConfigColor = JsonConfigColor.create(this)
            itemAdapter.apply {
                add(HttpDetailOverviewItemView(detail, calendar))
                add(
                    HttpDetailContentItemView(
                        detail, R.id.vh_item_http_detail_request.toLong(),
                        lifecycleScope, lifecycle, jsonConfigColor
                    )
                )
                add(
                    HttpDetailContentItemView(
                        detail, R.id.vh_item_http_detail_response.toLong(),
                        lifecycleScope, lifecycle, jsonConfigColor
                    )
                )
            }
        }
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
        launch {
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
        launch {
            buildCURL(data).flowOn(Dispatchers.Main).collect { content ->
                if (setClipboardSafe(content)) {
                    Toast.makeText(this@HttpLogDetailActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareHarAsJsonFile(data: HttpLogDetailData) {
        launch {
            toSharableContent(data)
                .flowOn(Dispatchers.Main)
                .map { createCacheShareFile(TextualReport(InterAppJsonConverter.createJsonFileName(), it)) }
                .map { sendOrShareFileIntent(it, MIME_APP_JSON) }
                .collect {
                    startActivity(Intent.createChooser(it, getString(R.string.title_of_share)))
                }
        }
    }

    private fun copyHarToClipboard(data: HttpLogDetailData) {
        launch {
            toSharableContent(data)
                .flowOn(Dispatchers.Main)
                .collect { content ->
                    if (setClipboardSafe(content)) {
                        Toast.makeText(this@HttpLogDetailActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun toSharableContent(data: HttpLogDetailData): Flow<String> {
        return flow {
            val harEntryJson = data.getHarEntryAsGsonJsonObject()
            emit(InterAppJsonConverter.exportHARContent(harEntryJson))
        }.flowOn(Dispatchers.Default)
    }

    private fun onExportHarJsonFileIntentResult(activityResult: ActivityResult) {
        val value = viewModel.detailData.value ?: return
        launch {
            toSharableContent(value).flatMapConcat {
                createFlowForExportFileIntentResult(it, activityResult)
            }.collect {}
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        exportIntentLauncher.unregister()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val detailData = viewModel.detailData.value ?: return false
        return when (item.itemId) {
            R.id.action_http_share_har_json -> {
                shareHarAsJsonFile(detailData)
                true
            }

            R.id.action_http_share_as_curl -> {
                buildAndShareCURL(detailData)
                true
            }

            R.id.action_http_copy_har -> {
                copyHarToClipboard(detailData)
                true
            }

            R.id.action_http_copy_as_curl -> {
                buildAndCopyCURL(detailData)
                true
            }

            R.id.action_http_copy_response_content -> {
                val text = detailData.responseBody
                if (text == null) {
                    Toast.makeText(this@HttpLogDetailActivity, getString(R.string.msg_response_null), Toast.LENGTH_SHORT).show()
                } else if (setClipboardSafe(text)) {
                    Toast.makeText(this@HttpLogDetailActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
                true
            }

            R.id.action_http_copy_request_content -> {
                val text = detailData.requestBody
                if (text == null) {
                    Toast.makeText(this@HttpLogDetailActivity, getString(R.string.msg_response_null), Toast.LENGTH_SHORT).show()
                } else if (setClipboardSafe(text)) {
                    Toast.makeText(this@HttpLogDetailActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
                true
            }

            R.id.action_http_export_har_as_json -> {
                requestFileUriForHarExport(getString(R.string.menu_http_export_har_as_json))
                true
            }

            else -> super.onOptionsItemSelected(item)
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