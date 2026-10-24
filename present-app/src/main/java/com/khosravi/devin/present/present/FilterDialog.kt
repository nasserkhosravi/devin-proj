package com.khosravi.devin.present.present

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.filter.CustomFilterCriteria
import com.khosravi.devin.present.filter.CustomFilterItem
import com.khosravi.devin.present.filter.FilterUiData
import com.khosravi.devin.present.itsNotEmpty
import com.khosravi.devin.present.uikit.theme.spacing

/**
 * Creates a custom filter from one or more exact tags, plus optional search text. [availableTags] are the tags the
 * client has logged (null while loading); [preselectedTags] start checked and are shown even when not available.
 * A blank title defaults to the picked tags joined with " + ". Field values survive rotation.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    availableTags: List<String>?,
    preselectedTags: List<String>,
    onConfirm: (CustomFilterItem) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var searchText by rememberSaveable { mutableStateOf("") }
    // In pick order; an ArrayList so it can be saved.
    var picked by rememberSaveable { mutableStateOf(ArrayList(preselectedTags.distinct())) }
    var showTagError by rememberSaveable { mutableStateOf(false) }

    val shownTags = remember(availableTags, preselectedTags) {
        availableTags?.let { available -> preselectedTags.filter { it !in available }.distinct() + available }
    }

    Surface(shape = MaterialTheme.shapes.medium) {
        // Scrolls as a whole so Confirm stays reachable when the window is short (landscape).
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.medium)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.field_title)) },
                placeholder = if (picked.isEmpty()) null else {
                    { Text(picked.joinToString(TITLE_SEPARATOR), maxLines = 1) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(MaterialTheme.spacing.medium))
            Text(
                text = stringResource(R.string.field_filter_tags),
                style = MaterialTheme.typography.titleSmall,
                color = if (showTagError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            when {
                shownTags == null -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.medium)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                shownTags.isEmpty() -> Text(
                    text = stringResource(R.string.filter_no_tags),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.small)
                )

                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    modifier = Modifier
                        .heightIn(max = TAGS_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                ) {
                    shownTags.forEach { tag ->
                        val isPicked = tag in picked
                        FilterChip(
                            selected = isPicked,
                            onClick = {
                                picked = ArrayList(if (isPicked) picked - tag else picked + tag)
                                showTagError = false
                            },
                            label = { Text(tag) },
                            leadingIcon = if (isPicked) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                        )
                    }
                }
            }
            if (showTagError) {
                Text(
                    text = stringResource(R.string.filter_error_no_tag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(MaterialTheme.spacing.small))
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text(stringResource(R.string.field_search_text)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(MaterialTheme.spacing.medium))
            Button(
                enabled = !shownTags.isNullOrEmpty(),
                onClick = {
                    if (picked.isEmpty()) {
                        showTagError = true
                        return@Button
                    }
                    val fTitle = title.trim().ifEmpty { picked.joinToString(TITLE_SEPARATOR) }
                    onConfirm(
                        CustomFilterItem(
                            ui = FilterUiData(fTitle, fTitle.itsNotEmpty(), false),
                            criteria = CustomFilterCriteria(picked.toList(), searchText.ifBlank { null })
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

private const val TITLE_SEPARATOR = " + "
private val TAGS_MAX_HEIGHT = 240.dp
