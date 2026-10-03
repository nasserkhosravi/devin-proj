package com.khosravi.devin.present.present

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.R
import com.khosravi.devin.present.analytics.Analytics
import com.khosravi.devin.present.analytics.Analytics.ClientOpenSource
import com.khosravi.devin.present.analytics.Analytics.UpdatePromptAction
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.client.ClientItem
import com.khosravi.devin.present.client.getLogPassword
import com.khosravi.devin.present.data.ClientLoadedState
import com.khosravi.devin.present.databinding.ActivityStarterBinding
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.domain.ClientLoginInteractor
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.notification.LogNotificationLaunchCoordinator
import com.khosravi.devin.present.update.GitHubReleaseSource
import com.khosravi.devin.present.update.ReleaseInfo
import com.khosravi.devin.present.update.UpdateChecker
import com.khosravi.devin.present.update.openReleasePage
import com.khosravi.devin.present.update.updateMessage
import com.khosravi.devin.present.update.updateTitle
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
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
    private var footerDefaultColors: ColorStateList? = null
    private var forceUpdateDialog: AlertDialog? = null
    private var optionalUpdateDialog: AlertDialog? = null

    /** Auto-routing to logs held back while [optionalUpdateDialog] is open; runs when it closes. */
    private var pendingRoute: (() -> Unit)? = null

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    private var _binding: ActivityStarterBinding? = null

    private val binding: ActivityStarterBinding
        get() = _binding!!
    private val itemAdapter = ItemAdapter<ClientItem>()
    private val adapter = FastAdapter.with(itemAdapter)

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityStarterBinding.inflate(LayoutInflater.from(this), null, false)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        notificationLaunchCoordinator.readTarget(intent)

        adapter.onClickListener = { _, _, item: ClientItem, _ ->
            onSelectClient(item.data, ClientOpenSource.LIST)
            true
        }

        launchGettingClientList()

    }

    override fun onResume() {
        super.onResume()
        updateJob = launch {
            updateChecker.availableUpdate.collect {
                bindFooter(it)
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

    private fun bindFooter(update: ReleaseInfo?) {
        if (footerDefaultColors == null) footerDefaultColors = binding.tvFooter.textColors
        if (update == null) {
            footerDefaultColors?.let(binding.tvFooter::setTextColor)
        } else {
            binding.tvFooter.setTextColor(ContextCompat.getColor(this, R.color.update_available_text))
        }
        binding.tvFooter.text = if (update == null) {
            getString(R.string.starter_footer, BuildConfig.VERSION_NAME, CONTRIBUTOR_NAME)
        } else {
            getString(R.string.starter_footer_update, BuildConfig.VERSION_NAME, CONTRIBUTOR_NAME, update.version.toString())
        }
        val url = update?.pageUrl ?: GitHubReleaseSource.RELEASES_PAGE_URL
        binding.tvFooter.setOnClickListener {
            Analytics.updateFooterClicked(hasUpdate = update != null)
            openReleasePage(url)
        }
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
        Analytics.updatePromptShown(isForceUpdate = true)
        forceUpdateDialog = AlertDialog.Builder(this)
            .setTitle(updateTitle(update))
            .setMessage(updateMessage(update))
            .setPositiveButton(R.string.update_action_download) { _, _ ->
                Analytics.updatePromptAction(isForceUpdate = true, UpdatePromptAction.DOWNLOAD)
                openReleasePage(update.pageUrl)
            }
            .setOnCancelListener {
                Analytics.updatePromptAction(isForceUpdate = true, UpdatePromptAction.DISMISS)
                finish()
            }
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
        Analytics.updatePromptShown(isForceUpdate = false)
        optionalUpdateDialog = AlertDialog.Builder(this)
            .setTitle(updateTitle(pending))
            .setMessage(updateMessage(pending))
            .setPositiveButton(R.string.update_action_download) { _, _ ->
                Analytics.updatePromptAction(isForceUpdate = false, UpdatePromptAction.DOWNLOAD)
                updateChecker.markPrompted()
                openReleasePage(pending.pageUrl)
            }
            .setNegativeButton(R.string.update_action_later) { _, _ ->
                Analytics.updatePromptAction(isForceUpdate = false, UpdatePromptAction.LATER)
                updateChecker.markPrompted()
            }
            .setOnCancelListener {
                Analytics.updatePromptAction(isForceUpdate = false, UpdatePromptAction.DISMISS)
                updateChecker.markPrompted()
            }
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
            binding.tvMessage.text = getString(R.string.loading)
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
                Analytics.clientOpened(
                    ClientOpenSource.NOTIFICATION,
                    isPasswordProtected = target.client.getLogPassword() != null
                )
                startActivity(Intent(this, LogActivity::class.java).apply {
                    target.tag?.let { putExtra(LogActivity.EXTRA_TARGET_TAG, it) }
                })
            } else {
                clientLoginInteractor.showManyTryPasswordToast(this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        forceUpdateDialog?.dismiss()
        forceUpdateDialog = null
        // Drop the held-back route so dismissing here doesn't navigate from a destroyed activity.
        pendingRoute = null
        optionalUpdateDialog?.dismiss()
        optionalUpdateDialog = null
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.starter_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshClients()
                true
            }

            else -> super.onOptionsItemSelected(item)
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
                itemAdapter.set(listOf(ClientItem(clientData)))

                binding.tvMessage.text = loadState.toStateMessage()
                routeAfterUpdatePrompt {
                    if (notificationTarget != null) {
                        onSelectNotificationTarget(notificationTarget)
                    } else {
                        onSelectClient(clientData, ClientOpenSource.AUTO)
                    }
                }
                binding.rvClients.adapter = adapter
            }

            is ClientLoadedState.Multi -> {
                itemAdapter.set(loadState.clients.map { ClientItem(it) })
                binding.tvMessage.text = loadState.toStateMessage()
                binding.rvClients.run {
                    val decorator = MaterialDividerItemDecoration(context, RecyclerView.VERTICAL)
                    addItemDecoration(decorator)
                    adapter = this@StarterActivity.adapter
                }
                notificationTarget?.let { routeAfterUpdatePrompt { onSelectNotificationTarget(it) } }
            }

            is ClientLoadedState.Zero -> {
                binding.tvMessage.text = loadState.toStateMessage()
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

    companion object {
        const val EXTRA_TARGET_CLIENT_ID = LogNotificationLaunchCoordinator.EXTRA_TARGET_CLIENT_ID
        const val EXTRA_TARGET_TAG = LogNotificationLaunchCoordinator.EXTRA_TARGET_TAG
        private const val CONTRIBUTOR_NAME = "nasser.khosravi"
    }

}
