package com.khosravi.devin.present.present.logitem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.khosravi.devin.present.uikit.theme.spacing

@Composable
fun SearchBarRow(
    sessionId: Int,
    text: String?,
    hint: String,
    onTextChange: (String?) -> Unit,
) {
    // `text` is only updated ~700ms after typing stops (LogActivity debounces the outgoing
    // search call). If this field's displayed value were bound directly to `text`, every
    // recomposition in that window would snap it back to the stale value, so keystrokes would
    // never visibly land. Local state is the actual source of truth for what's on screen.
    // Reset is keyed on `sessionId`, a counter LogActivity increments on every filter-chip tap
    // (including re-tapping the currently-selected chip) — matching the pre-migration original's
    // "always reset the search box on any chip tap" behavior. Keying on the filter's id alone
    // would miss the re-tap-same-chip case (id doesn't change then); keying on `hint` alone
    // would collide across different filters that share the same hint bucket string.
    var localText by remember(sessionId) { mutableStateOf(text ?: "") }

    OutlinedTextField(
        value = localText,
        onValueChange = {
            localText = it
            onTextChange(it.ifEmpty { null })
        },
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.xs)
    )
}
