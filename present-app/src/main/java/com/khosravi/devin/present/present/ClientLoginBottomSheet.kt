package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.khosravi.devin.present.uikit.theme.spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientLoginSheet(
    correctPassword: String,
    onCorrectPassword: (String) -> Unit,
    onWrongPassword: () -> Boolean,
    onDismissed: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissed() }
    }

    fun submit() {
        if (isSubmitting) return
        isSubmitting = true
        if (password == correctPassword) {
            onCorrectPassword(password)
            close()
        } else {
            val forceClose = onWrongPassword()
            if (forceClose) {
                close()
            } else {
                errorText = "Incorrect password. Please try again."
                isSubmitting = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { close() }, sheetState = sheetState) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.large)) {
            Text("Enter Numeric Password", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorText = null },
                label = { Text("Password") },
                singleLine = true,
                enabled = !isSubmitting,
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "HIDE" else "SHOW")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.large)
            )
            Button(
                onClick = { submit() },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.large)
            ) {
                Text("Confirm")
            }
        }
    }
}
