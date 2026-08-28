package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun LogDetailDialog(data: TextLogItemData, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(MaterialTheme.spacing.medium)
            ) {
                Text(data.tag, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(MaterialTheme.spacing.small))
                Text(data.text, style = MaterialTheme.typography.bodyMedium)
                data.meta?.let {
                    Spacer(Modifier.height(MaterialTheme.spacing.small))
                    Text(it.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
