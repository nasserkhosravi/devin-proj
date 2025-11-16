package com.khosravi.devin.present.present.clientparam

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.present.ClientParamsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientParamsScreen(viewModel: ClientParamsViewModel, clientId: String) {
    val textState by viewModel.text.collectAsState()
    var textFieldValue by remember { mutableStateOf(TextFieldValue(textState)) }

    // Update the TextField when the initial data is loaded from ViewModel
    LaunchedEffect(textState) {
        if (textFieldValue.text != textState) {
            textFieldValue = TextFieldValue(textState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(id = R.string.title_client_params)) })
        },
        bottomBar = {
            // Using a bottom bar for the save button to keep it visible
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        viewModel.saveParams(clientId, textFieldValue.text)
                    },
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(text = stringResource(id = R.string.action_save))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                label = { Text(stringResource(id = R.string.hint_client_params)) },
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .weight(1f), // Occupies most of the screen
                maxLines = Int.MAX_VALUE
            )
        }
    }
}