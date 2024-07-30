package com.khosravi.devin.present.present

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.khosravi.devin.present.R
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.client.ClientItem
import com.khosravi.devin.present.data.ClientLoadedState
import com.khosravi.devin.present.databinding.ActivityStarterBinding
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

class StarterActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    @Inject
    lateinit var vmFactory: ViewModelFactory

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

        adapter.onClickListener = { _, _, item: ClientItem, _ ->
            onSelectClient(item.data)
            true
        }
    }

    override fun onStart() {
        super.onStart()
        launch {
            viewModel.getClientList()
                .flowOn(Dispatchers.Main)
                .collect(::onClientListFetchResult)
        }
    }

    private fun onSelectClient(clientData: ClientData) {
        viewModel.setSelectedClientId(clientData)
        openNextActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
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
                viewModel.setSelectedClientId(loadState.client)
                openNextActivity()
                finish()
            }
            is ClientLoadedState.Multi -> {
                itemAdapter.set(loadState.clients.map { ClientItem(it) })
                binding.tvMessage.text = loadState.toStateMessage()
                binding.rvClients.adapter = adapter
            }
            is ClientLoadedState.Zero -> {
                binding.tvMessage.text = loadState.toStateMessage()
            }
        }
    }

    private fun openNextActivity() {
        startActivity(Intent(this@StarterActivity, LogActivity::class.java))
    }

}
