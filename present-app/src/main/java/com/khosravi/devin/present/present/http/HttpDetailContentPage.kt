package com.khosravi.devin.present.present.http

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.uikit.theme.devinColors
import com.khosravi.devin.present.uikit.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Request or Response tab: headers, then (if any) the copy row and the searchable, JSON-coloured body. */
@Composable
internal fun HttpDetailContentPage(
    data: HttpLogDetailData,
    side: HttpContentSide,
    onCopyText: (String) -> Unit,
    onCopyCurl: () -> Unit,
) {
    val context = LocalContext.current
    // Declared before the loading branch so both are restored after recreation.
    val listState = rememberLazyListState()
    val search = rememberSaveable(saver = BodySearchState.Saver) { BodySearchState() }

    val model by produceState<HttpContentModel?>(initialValue = null, data, side) {
        val jsonConfigColor = JsonConfigColor.create(context)
        value = withContext(Dispatchers.Default) {
            buildHttpContentModel(context, data, side, jsonConfigColor)
        }
    }

    val current = model
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val body = current.body
    if (body != null) {
        LaunchedEffect(body, search.query) {
            val query = search.query
            val result = if (query.isNotBlank() && query.length > MIN_QUERY_LENGTH_EXCLUSIVE) {
                withContext(Dispatchers.Default) { searchBody(body.lines, query) }
            } else {
                null
            }
            val isNewQuery = search.applyResult(query, result)
            // Like before: once typing pauses, jump to the first match. A restored query keeps its position.
            if (isNewQuery && search.currentMatch != null) {
                delay(SEARCH_SCROLL_DELAY_MS)
                // Re-read: the user may have moved with up/down during the delay; scroll to what the counter shows.
                val target = search.currentMatch ?: return@LaunchedEffect
                listState.animateScrollToItem(body.itemIndexOf(target) + current.bodyItemsOffset)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (body != null) {
            BodySearchBar(search = search, body = body, bodyItemsOffset = current.bodyItemsOffset, listState = listState)
        }
        HttpContentList(
            model = current,
            search = search,
            listState = listState,
            onCopyBody = { body?.let { onCopyText(it.formattedText) } },
            onCopyCurl = onCopyCurl,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodySearchBar(
    search: BodySearchState,
    body: HttpBodyModel,
    bodyItemsOffset: Int,
    listState: LazyListState,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = search.query,
        onValueChange = { search.query = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.xs),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.hint_search_in_body)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (search.query.isNotEmpty()) {
                IconButton(onClick = { search.query = "" }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )

    val matchCount = search.result?.matches?.size ?: 0
    if (matchCount > 0 && search.currentIndex >= 0) {
        fun moveTo(goNext: Boolean) {
            val target = search.move(goNext) ?: return
            scope.launch { listState.animateScrollToItem(body.itemIndexOf(target) + bodyItemsOffset) }
        }

        val onBar = MaterialTheme.devinColors.searchCounterText
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.devinColors.searchCounterBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${search.currentIndex + 1} / $matchCount",
                color = onBar,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
            )
            IconButton(onClick = { moveTo(goNext = false) }) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.search_previous_match),
                    tint = onBar,
                )
            }
            IconButton(onClick = { moveTo(goNext = true) }) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.search_next_match),
                    tint = onBar,
                )
            }
        }
    }
}

@Composable
private fun HttpContentList(
    model: HttpContentModel,
    search: BodySearchState,
    listState: LazyListState,
    onCopyBody: () -> Unit,
    onCopyCurl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalPadding = MaterialTheme.spacing.medium
    val body = model.body
    val colors = MaterialTheme.devinColors
    val highlightStyles = remember(colors) {
        searchHighlightStyles(
            matchText = colors.searchMatchText,
            matchBackground = colors.searchMatchBackground,
            currentText = colors.searchCurrentMatchText,
            currentBackground = colors.searchCurrentMatchBackground,
        )
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MaterialTheme.spacing.medium),
    ) {
        // Header chunks, then the copy row (or the empty-body message); body items start at bodyItemsOffset.
        items(count = model.headers.size, key = { "header_$it" }) { index ->
            val chunk = model.headers[index]
            // A chunk boundary right after '\n' would add a blank line, since each chunk starts a new row.
            val display = remember(chunk) {
                if (index < model.headers.lastIndex && chunk.text.endsWith('\n')) chunk.subSequence(0, chunk.length - 1) else chunk
            }
            SelectionContainer {
                Text(
                    text = display,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }
        }
        if (body == null) {
            item(key = "empty") {
                BodyText(
                    text = AnnotatedString(stringResource(R.string.msg_body_empty)),
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }
        } else {
            item(key = "copy") {
                CopyActionsRow(
                    onCopyBody = onCopyBody,
                    onCopyCurl = onCopyCurl,
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
            }
            items(count = body.items.size) { index ->
                val item = body.items[index]
                val result = search.result
                val currentMatch = search.currentMatch
                val text = remember(item, result, currentMatch, highlightStyles) {
                    item.highlighted(result, currentMatch, highlightStyles)
                }
                SelectionContainer {
                    BodyText(text = text, modifier = Modifier.padding(horizontal = horizontalPadding))
                }
            }
        }
    }
}

@Composable
private fun BodyText(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FiraCode,
            textDirection = TextDirection.Ltr,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun CopyActionsRow(onCopyBody: () -> Unit, onCopyCurl: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CopyAction(text = stringResource(R.string.menu_http_copy_body), onClick = onCopyBody)
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
        CopyAction(text = stringResource(R.string.menu_http_copy_curl_short), onClick = onCopyCurl)
    }
}

@Composable
private fun CopyAction(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.baseline_content_copy_24),
            contentDescription = null,
            modifier = Modifier.padding(MaterialTheme.spacing.xs),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Search state of one content tab. [query], the current match index and the query the index belongs to
 * are saved, so a rotation keeps both the query and the position; the matches themselves are recomputed.
 */
@Stable
private class BodySearchState(
    query: String = "",
    currentIndex: Int = -1,
    private var appliedQuery: String? = null,
) {
    var query by mutableStateOf(query)
    var result by mutableStateOf<BodySearchResult?>(null)
        private set
    var currentIndex by mutableIntStateOf(currentIndex)
        private set

    val currentMatch: BodyMatch?
        get() = result?.matches?.getOrNull(currentIndex)

    /** Stores [newResult] for [forQuery]; returns true when it's for a different query than the last one. */
    fun applyResult(forQuery: String, newResult: BodySearchResult?): Boolean {
        val isNewQuery = forQuery != appliedQuery
        result = newResult
        val size = newResult?.matches?.size ?: 0
        currentIndex = when {
            size == 0 -> -1
            isNewQuery -> 0
            else -> currentIndex.coerceIn(0, size - 1)
        }
        appliedQuery = forQuery
        return isNewQuery
    }

    /** Moves to the next/previous match with wrap-around and returns it. */
    fun move(goNext: Boolean): BodyMatch? {
        val size = result?.matches?.size ?: return null
        if (size == 0) return null
        currentIndex = if (goNext) (currentIndex + 1) % size else (currentIndex - 1 + size) % size
        return currentMatch
    }

    companion object {
        val Saver = listSaver<BodySearchState, Any?>(
            save = { listOf(it.query, it.currentIndex, it.appliedQuery) },
            restore = { BodySearchState(it[0] as String, it[1] as Int, it[2] as String?) },
        )
    }
}

private val FiraCode = FontFamily(Font(R.font.firacode_regular))

/** Queries must be longer than this, as before the migration. */
private const val MIN_QUERY_LENGTH_EXCLUSIVE = 1

private const val SEARCH_SCROLL_DELAY_MS = 600L

