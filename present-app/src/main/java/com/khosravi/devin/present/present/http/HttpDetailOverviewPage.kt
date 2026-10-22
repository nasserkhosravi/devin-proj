package com.khosravi.devin.present.present.http

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.uikit.theme.spacing

/** Overview tab: label/value rows as in the old `item_http_detail_overview.xml`; values are selectable. */
@Composable
internal fun HttpDetailOverviewPage(data: HttpLogDetailData, calendar: CalendarProxy) {
    val requestTime = remember(data) { data.requestDateString(calendar) }
    val responseTime = remember(data) { data.responseDateString(calendar) }
    val groupGap = MaterialTheme.spacing.small
    SelectionContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.medium)
        ) {
            OverviewRow(stringResource(R.string.url), data.urlExternalForm)
            OverviewRow(stringResource(R.string.method), data.harRequest.method)
            OverviewRow(stringResource(R.string.protocol), data.protocol)
            OverviewRow(stringResource(R.string.status), data.statusString())
            OverviewRow(stringResource(R.string.response), data.responseSummaryText)

            OverviewRow(
                stringResource(R.string.ssl),
                stringResource(if (data.isSsl) R.string.yes else R.string.no),
                topGap = groupGap,
            )
            data.responseTlsVersion?.let { OverviewRow(stringResource(R.string.tls_version), it) }
            data.responseCipherSuite?.let { OverviewRow(stringResource(R.string.tls_cipher_suite), it) }

            OverviewRow(stringResource(R.string.request_time), requestTime, topGap = groupGap)
            OverviewRow(stringResource(R.string.response_time), responseTime)
            OverviewRow(stringResource(R.string.duration), data.durationString)

            OverviewRow(stringResource(R.string.request_size), data.requestSizeString, topGap = groupGap)
            OverviewRow(stringResource(R.string.response_size), data.responseSizeString)
            OverviewRow(stringResource(R.string.total_size), data.totalSizeString)
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String?, topGap: Dp = 0.dp) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = topGap)) {
        DisableSelection {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(LABEL_WEIGHT),
            )
        }
        Text(
            text = value.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f - LABEL_WEIGHT)
                .padding(start = MaterialTheme.spacing.medium),
        )
    }
}

/** Matches the old XML guideline at 28% of the content width. */
private const val LABEL_WEIGHT = 0.28f
