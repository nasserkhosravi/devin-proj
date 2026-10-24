package com.khosravi.devin.present.present

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.khosravi.devin.present.KEY_DATA
import com.khosravi.devin.present.R
import com.khosravi.devin.present.analytics.Analytics
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.getParcelableExtraSupport
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.uikit.theme.DevinTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

class ImportLogActivity : BaseActivity() {

    @Inject
    lateinit var calendar: CalendarProxy

    @Inject
    lateinit var vmFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    /** Null until the imported file has been read and converted. */
    private var items by mutableStateOf<List<LogItemData>?>(null)

    /** The import result is reported once, not again when it is replayed after a configuration change. */
    private var isImportResultReported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        isImportResultReported = savedInstanceState != null

        setContent {
            DevinTheme { ImportLogNavHost() }
        }
        // No-op after a configuration change: the ViewModel keeps the already loaded result.
        viewModel.loadImportedLogs(intent.getParcelableExtraSupport(KEY_DATA, Uri::class.java))
        observeImportedLogs()
    }

    @Composable
    private fun ImportLogNavHost() {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = ROUTE_IMPORTED) {
            composable(ROUTE_IMPORTED) {
                ImportLogScreen(
                    calendar = calendar,
                    items = items,
                    onTextLogClick = {
                        // Ignore a second tap that lands while the detail transition is already running.
                        if (navController.isCurrentEntryResumed()) {
                            Analytics.logDetailOpened(Analytics.LogType.TEXT, Analytics.LogSource.IMPORTED)
                            LogDetailArgs.navigate(navController, it)
                        }
                    },
                    onHttpLogClick = {
                        //TODO: support http detail viewer
                        Toast.makeText(this@ImportLogActivity, "Coming soon", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            logDetailDestination(navController, calendar, startRoute = ROUTE_IMPORTED, appPackage = null)
        }
    }

    private fun observeImportedLogs() {
        lifecycleScope.launch {
            viewModel.importedLogs.collect { result ->
                if (result != null && !isImportResultReported) {
                    isImportResultReported = true
                    Analytics.logsImported(isSuccessful = result is ReaderViewModel.ImportResult.Loaded)
                }
                when (result) {
                    null -> Unit // still loading
                    is ReaderViewModel.ImportResult.Loaded -> items = result.logs
                    ReaderViewModel.ImportResult.ReadFailed -> finishWithError(R.string.error_msg_something_went_wrong)
                    ReaderViewModel.ImportResult.Empty -> finishWithError(R.string.error_msg_empty_file_text)
                }
            }
        }
    }

    private fun finishWithError(@StringRes messageRes: Int) {
        if (isFinishing) return
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        private const val ROUTE_IMPORTED = "imported"

        fun intent(context: Context, logUri: Uri) = Intent(context, ImportLogActivity::class.java).apply {
            putExtra(KEY_DATA, logUri)
        }
    }
}
