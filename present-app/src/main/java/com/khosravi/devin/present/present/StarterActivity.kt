package com.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.khosravi.devin.present.R
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.client.ClientItem
import com.khosravi.devin.present.data.ClientLoadedState
import com.khosravi.devin.present.databinding.ActivityStarterBinding
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.domain.ClientLoginInteractor
import com.khosravi.devin.present.arch.BaseActivity
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

class StarterActivity : BaseActivity() {

    @Inject
    lateinit var vmFactory: ViewModelFactory

    @Inject
    lateinit var clientLoginInteractor: ClientLoginInteractor

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

        adapter.onClickListener = { _, _, item: ClientItem, _ ->
            onSelectClient(item.data)
            true
        }

        launchGettingClientList()

    }

    private fun launchGettingClientList() {
        launch {
            binding.tvMessage.text = getString(R.string.loading)
            //delay to let user see loading text a
            delay(100)
            viewModel.getClientList()
                .flowOn(Dispatchers.Main)
                .collect(::onClientListFetchResult)
        }
    }

    private fun onSelectClient(clientData: ClientData) {
        viewModel.setSelectedClientId(clientData)
        clientLoginInteractor.onClientSelect(this, clientData) {
            isRouteSuccessful(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
        when (loadState) {
            is ClientLoadedState.Single -> {
                val clientData = loadState.client
                itemAdapter.set(listOf(ClientItem(clientData)))

                binding.tvMessage.text = loadState.toStateMessage()
                viewModel.setSelectedClientId(clientData)
                clientLoginInteractor.onClientSelect(this, clientData) {
                    isRouteSuccessful(it)
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
            }

            is ClientLoadedState.Zero -> {
                binding.tvMessage.text = loadState.toStateMessage()
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

}
