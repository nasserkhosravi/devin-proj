package com.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.R
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.data.ClientLoadedState
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.domain.ClientLoginInteractor
import com.khosravi.devin.present.notification.LogNotificationLaunchCoordinator
import com.khosravi.devin.present.uikit.theme.DevinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

class StarterActivity : BaseActivity() {

    private val notificationLaunchCoordinator = LogNotificationLaunchCoordinator(this) {
        onClientListFetchResult(it)
    }

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var clientLoginInteractor: ClientLoginInteractor

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
    }

    private var message by mutableStateOf("")
    private var clients by mutableStateOf(emptyList<ClientData>())

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
                    onRefresh = ::refreshClients
                )
            }
        }

        launchGettingClientList()
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
            delay(100)
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
        viewModel.setSelectedClientId(clientData)
        clientLoginInteractor.onClientSelect(this, clientData) {
            isRouteSuccessful(it)
        }
    }

    private fun onSelectNotificationTarget(target: LogNotificationLaunchCoordinator.Target) {
        viewModel.setSelectedClientId(target.client)
        clientLoginInteractor.onClientSelect(this, target.client) { canRoute ->
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
                if (notificationTarget != null) {
                    onSelectNotificationTarget(notificationTarget)
                } else {
                    viewModel.setSelectedClientId(clientData)
                    clientLoginInteractor.onClientSelect(this, clientData) {
                        isRouteSuccessful(it)
                    }
                }
            }

            is ClientLoadedState.Multi -> {
                clients = loadState.clients
                message = loadState.toStateMessage()
                notificationTarget?.let(::onSelectNotificationTarget)
            }

            is ClientLoadedState.Zero -> {
                clients = emptyList()
                message = loadState.toStateMessage()
            }
        }
    }

    private fun isRouteSuccessful(canRoute: Boolean) {
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
    }

}
