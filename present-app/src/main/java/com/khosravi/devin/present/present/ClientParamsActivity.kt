package com.khosravi.devin.present.present

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.khosravi.devin.present.R
import com.khosravi.devin.present.di.ViewModelFactory
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.present.clientparam.ClientParamsScreen
import kotlinx.coroutines.launch
import javax.inject.Inject

class ClientParamsActivity : ComponentActivity() {

    @Inject
    lateinit var vmFactory: ViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(this, vmFactory)[ClientParamsViewModel::class.java]
    }

    private val clientId by lazy {
        intent.getStringExtra(EXTRA_CLIENT_ID)
            ?: throw IllegalArgumentException("Client ID is required but was not provided.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this) // Assuming you have this for injection
        super.onCreate(savedInstanceState)

        viewModel.loadParams(clientId)

        setContent {
            // Replace with your app's theme if you have one
            MaterialTheme {
                ClientParamsScreen(viewModel = viewModel, clientId = clientId)
            }
        }

        observeSaveStatus()
    }

    private fun observeSaveStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveStatus.collect { status ->
                    when (status) {
                        is ClientParamsViewModel.SaveStatus.Success -> {
                            Toast.makeText(this@ClientParamsActivity, R.string.msg_params_saved, Toast.LENGTH_SHORT).show()
                            viewModel.resetSaveStatus() // Reset status after showing toast
                            finish() // Close activity after successful save
                        }

                        is ClientParamsViewModel.SaveStatus.Error -> {
                            Toast.makeText(this@ClientParamsActivity, status.message, Toast.LENGTH_LONG).show()
                            viewModel.resetSaveStatus()
                        }

                        ClientParamsViewModel.SaveStatus.Idle -> {
                            // Do nothing
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_CLIENT_ID = "extra_client_id"

        fun newIntent(context: Context, clientId: String): Intent {
            return Intent(context, ClientParamsActivity::class.java).apply {
                putExtra(EXTRA_CLIENT_ID, clientId)
            }
        }
    }
}