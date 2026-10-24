package com.khosravi.devin.present.present

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.R
import com.khosravi.devin.present.analytics.Analytics
import com.khosravi.devin.present.analytics.Analytics.ClientOpenSource
import com.khosravi.devin.present.analytics.Analytics.UpdatePromptAction
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.client.getLogPassword
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** Auto-routing to logs held back while [optionalUpdatePrompt] is open; runs when it closes. */
    private var pendingRoute: (() -> Unit)? = null

    /** The optional update prompt currently shown, or null. */
    private var optionalUpdatePrompt by mutableStateOf<ReleaseInfo?>(null)

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    private var message by mutableStateOf("")
    private var clients by mutableStateOf(emptyList<ClientData>())

    /** Label, icon and log count per client package id; filled in after [clients] loads and on every resume. */
    private var clientInfo by mutableStateOf(emptyMap<String, ClientDisplayInfo>())
    private var clientInfoJob: Job? = null
    private var availableUpdate by mutableStateOf<ReleaseInfo?>(null)

    /** Refreshed on every resume, so it follows the permission dialog and changes made in system settings. */
    private var areNotificationsEnabled by mutableStateOf(true)

    /** The force update already reported as shown, so it is reported once per activity, not on every resume. */
    private var reportedForceUpdate: ReleaseInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        notificationLaunchCoordinator.readTarget(intent)

        setContent {
            DevinTheme {
                StarterScreen(
                    message = message,
                    clients = clients,
                    clientInfo = clientInfo,
                    onClientClick = { onSelectClient(it, ClientOpenSource.LIST) },
                    areNotificationsEnabled = areNotificationsEnabled,
                    onNotificationsDisabledClick = ::openNotificationSettings,
                    onRefresh = ::refreshClients,
                    footerText = footerText(availableUpdate),
                    isUpdateAvailable = availableUpdate != null,
                    onFooterClick = {
                        Analytics.updateFooterClicked(hasUpdate = availableUpdate != null)
                        openReleasePage(availableUpdate?.pageUrl ?: GitHubReleaseSource.RELEASES_PAGE_URL)
                    },
                    forceUpdate = availableUpdate?.takeIf { it.isForceUpdate },
                    onForceUpdateDownload = { update ->
                        Analytics.updatePromptAction(isForceUpdate = true, UpdatePromptAction.DOWNLOAD)
                        openReleasePage(update.pageUrl)
                    },
                    // Back on the force-update dialog leaves the app; it can't be dismissed otherwise.
                    onForceUpdateDismiss = {
                        Analytics.updatePromptAction(isForceUpdate = true, UpdatePromptAction.DISMISS)
                        finish()
                    },
                    optionalUpdate = optionalUpdatePrompt,
                    onOptionalUpdateDownload = { closeOptionalUpdatePrompt(UpdatePromptAction.DOWNLOAD) },
                    onOptionalUpdateClose = { closeOptionalUpdatePrompt(UpdatePromptAction.DISMISS) },
                    onOptionalUpdateLater = { closeOptionalUpdatePrompt(UpdatePromptAction.LATER) },
                )
            }
        }

        launchGettingClientList()
    }

    override fun onResume() {
        super.onResume()
        areNotificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        // Log counts change while the log list is open (new logs, clear), so re-read them on return.
        loadClientInfo()
        updateJob = launch {
            updateChecker.availableUpdate.collect {
                availableUpdate = it
                reportForceUpdateShownIfNeeded(it)
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

    private fun openNotificationSettings() {
        Analytics.notificationSettingsOpened()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        }
        startActivity(intent)
    }

    private fun footerText(update: ReleaseInfo?): String = if (update == null) {
        getString(R.string.starter_footer, BuildConfig.VERSION_NAME, CONTRIBUTOR_NAME)
    } else {
        getString(R.string.starter_footer_update, BuildConfig.VERSION_NAME, CONTRIBUTOR_NAME, update.version.toString())
    }

    /**
     * Shown up to 3 times a day until updated, checked on every resume; answering it ("Later", back,
     * outside tap, "Download") counts one. An open prompt isn't counted, so after a configuration
     * change the recreated activity shows it again from onResume.
     * The force-update dialog needs no trigger: it's visible whenever [availableUpdate] is a force update.
     */
    private fun showOptionalUpdateDialogIfNeeded(update: ReleaseInfo?) {
        if (update == null || update.isForceUpdate) return
        if (optionalUpdatePrompt != null) return
        optionalUpdatePrompt = updateChecker.pendingPrompt() ?: return
        Analytics.updatePromptShown(isForceUpdate = false)
    }

    private fun reportForceUpdateShownIfNeeded(update: ReleaseInfo?) {
        if (update?.isForceUpdate != true || update == reportedForceUpdate) return
        reportedForceUpdate = update
        Analytics.updatePromptShown(isForceUpdate = true)
    }

    private fun closeOptionalUpdatePrompt(action: UpdatePromptAction) {
        val prompt = optionalUpdatePrompt ?: return
        optionalUpdatePrompt = null
        Analytics.updatePromptAction(isForceUpdate = false, action)
        val openPage = action == UpdatePromptAction.DOWNLOAD
        updateChecker.markPrompted()
        val route = pendingRoute
        pendingRoute = null
        if (openPage) {
            // Leave the release page in front; routing on now would open the log list over the browser.
            // The client list stays here for when the user comes back.
            openReleasePage(prompt.pageUrl)
            return
        }
        route?.invoke()
    }

    /** Runs [route] now, or once the optional update prompt is closed if it is showing. */
    private fun routeAfterUpdatePrompt(route: () -> Unit) {
        if (optionalUpdatePrompt != null) {
            pendingRoute = route
        } else {
            route()
        }
    }

    /** True (and shows the force-update dialog) if a force update must block routing to logs. */
    private fun isBlockedByForceUpdate(): Boolean {
        val update = updateChecker.availableUpdate.value
        if (update?.isForceUpdate != true) return false
        availableUpdate = update
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

    private fun onSelectClient(clientData: ClientData, source: ClientOpenSource) {
        if (isBlockedByForceUpdate()) return
        viewModel.setSelectedClientId(clientData)
        clientLoginInteractor.onClientSelect(this, clientData) {
            if (it) Analytics.clientOpened(source, isPasswordProtected = clientData.getLogPassword() != null)
            isRouteSuccessful(it)
        }
    }

    private fun onSelectNotificationTarget(target: LogNotificationLaunchCoordinator.Target) {
        if (isBlockedByForceUpdate()) return
        viewModel.setSelectedClientId(target.client)
        clientLoginInteractor.onClientSelect(this, target.client) { canRoute ->
            if (isBlockedByForceUpdate()) return@onClientSelect
            if (canRoute) {
                Analytics.clientOpened(ClientOpenSource.NOTIFICATION, isPasswordProtected = target.client.getLogPassword() != null)
                startActivity(Intent(this, LogActivity::class.java).apply {
                    target.tag?.let { putExtra(LogActivity.EXTRA_TARGET_TAG, it) }
                })
            } else {
                clientLoginInteractor.showManyTryPasswordToast(this)
            }
        }
    }

    private fun loadClientInfo() {
        val current = clients
        if (current.isEmpty()) return
        clientInfoJob?.cancel()
        clientInfoJob = launch {
            clientInfo = withContext(Dispatchers.Default) {
                current.associate { it.packageId to loadClientDisplayInfo(applicationContext, it) }
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
        Analytics.clientListLoaded(
            when (loadState) {
                is ClientLoadedState.Zero -> 0
                is ClientLoadedState.Single -> 1
                is ClientLoadedState.Multi -> loadState.clients.size
            }
        )

        when (loadState) {
            is ClientLoadedState.Single -> {
                val clientData = loadState.client
                clients = listOf(clientData)
                loadClientInfo()
                message = loadState.toStateMessage()
                routeAfterUpdatePrompt {
                    if (notificationTarget != null) {
                        onSelectNotificationTarget(notificationTarget)
                    } else {
                        onSelectClient(clientData, ClientOpenSource.AUTO)
                    }
                }
            }

            is ClientLoadedState.Multi -> {
                clients = loadState.clients
                loadClientInfo()
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
        // Drop the held-back route so it never navigates from a destroyed activity.
        pendingRoute = null
    }

    companion object {
        const val EXTRA_TARGET_CLIENT_ID = LogNotificationLaunchCoordinator.EXTRA_TARGET_CLIENT_ID
        const val EXTRA_TARGET_TAG = LogNotificationLaunchCoordinator.EXTRA_TARGET_TAG
        private const val CONTRIBUTOR_NAME = "nasser.khosravi"
    }

}
