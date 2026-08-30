package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.khosravi.devin.present.R
import com.khosravi.devin.present.filter.CustomFilterCriteria
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.itsNotEmpty
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun FilterDialog(onDismiss: () -> Unit, onConfirm: (CustomFilterItem) -> Unit) {
    var title by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(MaterialTheme.spacing.medium)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; showError = false },
                    label = { Text(stringResource(R.string.field_title)) },
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MaterialTheme.spacing.small))
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it; showError = false },
                    label = { Text(stringResource(R.string.field_filter_tag)) },
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MaterialTheme.spacing.small))
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(R.string.field_search_text)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MaterialTheme.spacing.small))
                Button(
                    onClick = {
                        if (title.isBlank() && tag.isBlank()) {
                            showError = true
                            return@Button
                        }
                        val fTitle = title.ifBlank { tag }
                        onConfirm(
                            CustomFilterItem(
                                ui = FilterUiData(fTitle, fTitle.itsNotEmpty(), false),
                                criteria = CustomFilterCriteria(tag.ifBlank { null }, searchText)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}
