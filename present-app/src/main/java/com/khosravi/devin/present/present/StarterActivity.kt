package com.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.R
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.ClientLoadedState
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.domain.ClientLoginInteractor
import com.khosravi.devin.present.notification.LogNotificationLaunchCoordinator
import com.khosravi.devin.present.uikit.theme.DevinTheme
import com.khosravi.devin.present.update.GitHubReleaseSource
import com.khosravi.devin.present.update.ReleaseInfo
import com.khosravi.devin.present.update.UpdateChecker
import com.khosravi.devin.present.update.openReleasePage
import com.khosravi.devin.present.update.updateMessage
import com.khosravi.devin.present.update.updateTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

class StarterActivity : BaseActivity() {

    private val notificationLaunchCoordinator = LogNotificationLaunchCoordinator(this) {
        onClientListFetchResult(it)
    }

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var clientLoginInteractor: ClientLoginInteractor

    @Inject
    lateinit var updateChecker: UpdateChecker

    private var updateJob: Job? = null
    private var forceUpdateDialog: AlertDialog? = null
    private var optionalUpdateDialog: AlertDialog? = null

    /** Auto-routing to logs held back while [optionalUpdateDialog] is open; runs when it closes. */
    private var pendingRoute: (() -> Unit)? = null

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    private var message by mutableStateOf("")
    private var clients by mutableStateOf(emptyList<ClientData>())
    private var availableUpdate by mutableStateOf<ReleaseInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        notificationLaunchCoordinator.readTarget(intent)

        setContent {
            DevinTheme {
                StarterScreen(
                    message = message,
                    clients = clients,
                    onClientClick = ::onSelectClient,
                    onRefresh = ::refreshClients,
                    footerText = footerText(availableUpdate),
                    isUpdateAvailable = availableUpdate != null,
                    onFooterClick = { openReleasePage(availableUpdate?.pageUrl ?: GitHubReleaseSource.RELEASES_PAGE_URL) },
                )
            }
        }

        launchGettingClientList()
    }

    override fun onResume() {
        super.onResume()
        updateJob = launch {
            updateChecker.availableUpdate.collect {
                availableUpdate = it
                showForceUpdateDialogIfNeeded(it)
                showOptionalUpdateDialogIfNeeded(it)
            }
        }
        updateChecker.checkInBackgroundIfDue()
    }

    override fun onPause() {
        super.onPause()
        updateJob?.cancel()
        updateJob = null
    }

    private fun footerText(update: ReleaseInfo?): String = if (update == null) {
        getString(R.string.starter_footer, BuildConfig.VERSION_NAME, CONTRIBUTOR_NAME)
    } else {
        getString(R.string.starter_footer_update, BuildConfig.VERSION_NAME, CONTRIBUTOR_NAME, update.version.toString())
    }

    /**
     * A major-version update blocks the app: the dialog can't be dismissed (back finishes the
     * activity) and is shown again on every resume, e.g. after returning from the release page.
     */
    private fun showForceUpdateDialogIfNeeded(update: ReleaseInfo?) {
        if (update?.isForceUpdate != true) {
            forceUpdateDialog?.dismiss()
            forceUpdateDialog = null
            return
        }
        if (forceUpdateDialog?.isShowing == true) return
        forceUpdateDialog = AlertDialog.Builder(this)
            .setTitle(updateTitle(update))
            .setMessage(updateMessage(update))
            .setPositiveButton(R.string.update_action_download) { _, _ -> openReleasePage(update.pageUrl) }
            .setOnCancelListener { finish() }
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
                show()
            }
    }

    /** Shown up to 3 times a day until updated; answering it ("Later", back, "Download") counts one. */
    private fun showOptionalUpdateDialogIfNeeded(update: ReleaseInfo?) {
        if (update == null || update.isForceUpdate) return
        if (optionalUpdateDialog?.isShowing == true) return
        val pending = updateChecker.pendingPrompt() ?: return
        optionalUpdateDialog = AlertDialog.Builder(this)
            .setTitle(updateTitle(pending))
            .setMessage(updateMessage(pending))
            .setPositiveButton(R.string.update_action_download) { _, _ ->
                updateChecker.markPrompted()
                openReleasePage(pending.pageUrl)
            }
            .setNegativeButton(R.string.update_action_later) { _, _ ->
                updateChecker.markPrompted()
            }
            .setOnCancelListener { updateChecker.markPrompted() }
            .setOnDismissListener {
                val route = pendingRoute
                pendingRoute = null
                route?.invoke()
            }
            .show()
    }

    /** Runs [route] now, or once the optional update dialog is closed if it is showing. */
    private fun routeAfterUpdatePrompt(route: () -> Unit) {
        if (optionalUpdateDialog?.isShowing == true) {
            pendingRoute = route
        } else {
            route()
        }
    }

    /** True (and shows the force-update dialog) if a force update must block routing to logs. */
    private fun isBlockedByForceUpdate(): Boolean {
        val update = updateChecker.availableUpdate.value
        if (update?.isForceUpdate != true) return false
        showForceUpdateDialogIfNeeded(update)
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationLaunchCoordinator.readTarget(intent)
        launchGettingClientList()
    }

    private fun launchGettingClientList() {
        launch {
            message = getString(R.string.loading)
            //delay to let user see loading text a
            delay(100.milliseconds)
            viewModel.getClientList()
                .flowOn(Dispatchers.Main)
                .collect {
                    if (!notificationLaunchCoordinator.requestPermissionIfNeeded(it)) {
                        onClientListFetchResult(it)
                    }
                }
        }
    }

    private fun onSelectClient(clientData: ClientData) {
        if (isBlockedByForceUpdate()) return
        viewModel.setSelectedClientId(clientData)
        clientLoginInteractor.onClientSelect(this, clientData) {
            isRouteSuccessful(it)
        }
    }

    private fun onSelectNotificationTarget(target: LogNotificationLaunchCoordinator.Target) {
        if (isBlockedByForceUpdate()) return
        viewModel.setSelectedClientId(target.client)
        clientLoginInteractor.onClientSelect(this, target.client) { canRoute ->
            if (isBlockedByForceUpdate()) return@onClientSelect
            if (canRoute) {
                startActivity(Intent(this, LogActivity::class.java).apply {
                    target.tag?.let { putExtra(LogActivity.EXTRA_TARGET_TAG, it) }
                })
            } else {
                clientLoginInteractor.showManyTryPasswordToast(this)
            }
        }
    }

    private fun refreshClients() {
        launchGettingClientList()
    }

    private fun ClientLoadedState.toStateMessage(): String {
        return when (this) {
            is ClientLoadedState.Zero -> getString(R.string.no_client_found)
            is ClientLoadedState.Single -> getString(R.string.one_client_found)
            is ClientLoadedState.Multi -> getString(R.string.choose_client)
        }
    }

    private fun onClientListFetchResult(loadState: ClientLoadedState) {
        val notificationTarget = notificationLaunchCoordinator.takeTarget(loadState)

        when (loadState) {
            is ClientLoadedState.Single -> {
                val clientData = loadState.client
                clients = listOf(clientData)
                message = loadState.toStateMessage()
                routeAfterUpdatePrompt {
                    if (notificationTarget != null) {
                        onSelectNotificationTarget(notificationTarget)
                    } else {
                        onSelectClient(clientData)
                    }
                }
            }

            is ClientLoadedState.Multi -> {
                clients = loadState.clients
                message = loadState.toStateMessage()
                notificationTarget?.let { routeAfterUpdatePrompt { onSelectNotificationTarget(it) } }
            }

            is ClientLoadedState.Zero -> {
                clients = emptyList()
                message = loadState.toStateMessage()
            }
        }
    }

    private fun isRouteSuccessful(canRoute: Boolean) {
        // The check may finish while the password sheet is open.
        if (isBlockedByForceUpdate()) return
        if (canRoute) {
            openNextActivity(this)
        } else {
            clientLoginInteractor.showManyTryPasswordToast(this)
        }
    }

    private fun openNextActivity(activity: AppCompatActivity) {
        activity.startActivity(Intent(activity, LogActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        forceUpdateDialog?.dismiss()
        forceUpdateDialog = null
        // Drop the held-back route so dismissing here doesn't navigate from a destroyed activity.
        pendingRoute = null
        optionalUpdateDialog?.dismiss()
        optionalUpdateDialog = null
    }

    companion object {
        const val EXTRA_TARGET_CLIENT_ID = LogNotificationLaunchCoordinator.EXTRA_TARGET_CLIENT_ID
        const val EXTRA_TARGET_TAG = LogNotificationLaunchCoordinator.EXTRA_TARGET_TAG
        private const val CONTRIBUTOR_NAME = "nasser.khosravi"
    }

}
