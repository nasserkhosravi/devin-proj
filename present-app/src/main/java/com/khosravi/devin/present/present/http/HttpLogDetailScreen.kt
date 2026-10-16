package com.khosravi.devin.present.present.http

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.date.CalendarProxy
import kotlinx.coroutines.launch

/** Overflow menu actions; same order, titles and icons as the old `http_detail_menu.xml`. */
enum class HttpDetailAction(@StringRes val title: Int, @DrawableRes val icon: Int) {
    ShareHarJson(R.string.menu_http_share_har_as_json, R.drawable.baseline_share_24),
    ShareCurl(R.string.menu_http_share_as_curl, R.drawable.baseline_share_24),
    CopyHar(R.string.menu_http_copy_har, R.drawable.baseline_content_copy_24),
    CopyCurl(R.string.menu_http_copy_as_curl, R.drawable.baseline_content_copy_24),
    CopyResponseContent(R.string.menu_http_copy_response_content, R.drawable.baseline_content_copy_24),
    CopyRequestContent(R.string.menu_http_copy_request_content, R.drawable.baseline_content_copy_24),
    SaveHarJson(R.string.menu_http_save_har_as_json, R.drawable.baseline_save_24),
}

private enum class HttpDetailTab(@StringRes val title: Int) {
    Overview(R.string.overview),
    Request(R.string.request),
    Response(R.string.response),
}

/**
 * HTTP log detail: Overview / Request / Response tabs over a swipeable pager, plus an overflow menu.
 * Shows a progress indicator until [data] is loaded, or an error text when [isLoadFailed]. [onCopyText] copies a text (the formatted body).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HttpLogDetailScreen(
    data: HttpLogDetailData?,
    isLoadFailed: Boolean,
    calendar: CalendarProxy,
    onAction: (HttpDetailAction) -> Unit,
    onCopyText: (String) -> Unit,
) {
    // Hoisted above the loading branch; it is saveable, so the selected tab survives recreation.
    val pagerState = rememberPagerState(pageCount = { HttpDetailTab.entries.size })
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (data == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoadFailed) {
                    Text(stringResource(R.string.error_msg_something_went_wrong))
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Surface
        }
        Column(modifier = Modifier.fillMaxSize()) {
            HttpDetailTopRow(pagerState = pagerState, onAction = onAction)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                // Keep every tab composed so swiping doesn't re-format a body or drop its search/scroll state.
                beyondBoundsPageCount = HttpDetailTab.entries.size - 1,
            ) { page ->
                when (HttpDetailTab.entries[page]) {
                    HttpDetailTab.Overview -> HttpDetailOverviewPage(data = data, calendar = calendar)
                    HttpDetailTab.Request -> HttpDetailContentPage(
                        data = data,
                        side = HttpContentSide.Request,
                        onCopyText = onCopyText,
                        onCopyCurl = { onAction(HttpDetailAction.CopyCurl) },
                    )

                    HttpDetailTab.Response -> HttpDetailContentPage(
                        data = data,
                        side = HttpContentSide.Response,
                        onCopyText = onCopyText,
                        onCopyCurl = { onAction(HttpDetailAction.CopyCurl) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HttpDetailTopRow(pagerState: PagerState, onAction: (HttpDetailAction) -> Unit) {
    val scope = rememberCoroutineScope()
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TabRow(
                // targetPage moves the indicator as soon as a swipe/click settles on a page.
                selectedTabIndex = pagerState.targetPage,
                modifier = Modifier.weight(1f),
            ) {
                HttpDetailTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.targetPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(tab.title)) },
                    )
                }
            }
            HttpDetailOverflowMenu(onAction = onAction)
        }
    }
}

@Composable
private fun HttpDetailOverflowMenu(onAction: (HttpDetailAction) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HttpDetailAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.title)) },
                    leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onAction(action)
                    },
                )
            }
        }
    }
}
