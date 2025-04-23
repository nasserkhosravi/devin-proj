package com.khosravi.devin.present.data.http

import com.google.gson.JsonObject
import com.khosravi.devin.present.present.http.HttpFormatUtils
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.DateTimePresent
import com.khosravi.devin.present.present.http.GsonConverter
import com.khosravi.lib.har.HarEntry
import com.khosravi.lib.har.HarHeader
import com.khosravi.lib.har.HarRequest
import java.time.Instant

data class HttpLogDetailData(
    val logId: Long,
    private val harEntry: HarEntry,
    private val transactionStatus: HttpLogOperationStatus,
    private val urlQuery: UrlQuery,
    val summeryOfError: String?,
) {
    private val startedAtMills by lazy { Instant.parse(harEntry.startedDateTime).toEpochMilli() }
    private val requestDateTimePresent by lazy { DateTimePresent(startedAtMills) }
    private val responseDateTimePresent by lazy { DateTimePresent(startedAtMills + harEntry.time) }

    fun getHarEntryAsGsonJsonObject(): JsonObject {
        val json = GsonConverter.instance.toJsonTree(harEntry)
        return json.asJsonObject
    }

    val isSsl: Boolean
        get() = urlQuery.isSsl
    val urlExternalForm
        get() = urlQuery.urlExternalForm
    val protocol: String
        get() = harEntry.response?.httpVersion ?: harEntry.request.httpVersion

    val durationString: String?
        get() = harEntry.time.let { if (it == -1L) null else "$it ms" }
    val requestSizeString: String?
        get() = harEntry.request.bodySize.let { if (it == -1L) null else formatBytes(it) }

    val responseTlsVersion: String?
        get() = harEntry.custom?.responseTlsVersion
    val responseCipherSuite: String?
        get() = harEntry.custom?.responseCipherSuite

    val responseSizeString: String?
        get() = harEntry.response?.bodySize?.let { if (it == -1L) null else formatBytes(it) }

    val responseSummaryText: String?
        get() {
            return when (transactionStatus) {
                HttpLogOperationStatus.NetworkFailed -> summeryOfError
                is HttpLogOperationStatus.Respond -> "${harEntry.response?.status} ${harEntry.response?.statusText}"
                else -> null
            }
        }

    val totalSizeString: String
        get() {
            val reqBytes = harEntry.request.bodySize.let { if (it == -1L) null else it } ?: 0
            val resBytes = harEntry.response?.bodySize?.let { if (it == -1L) null else it } ?: 0
            return formatBytes(reqBytes + resBytes)
        }

    val harRequest: HarRequest
        get() = harEntry.request
    val requestHeaders: List<HarHeader>
        get() = harEntry.request.headers
    val requestBody: String?
        get() = harEntry.request.postData?.text?.toString()

    val responseHeaders: List<HarHeader>
        get() = harEntry.response?.headers ?: emptyList()
    val requestBodyMimeType: String?
        get() = harEntry.request.postData?.mimeType
    val responseBody: String?
        get() = harEntry.response?.content?.text?.toString()
    val responseBodyMimeType: String?
        get() = harEntry.response?.content?.mimeType


    fun statusString(): String {
        return when (transactionStatus) {
            HttpLogOperationStatus.Requested -> "Requested"
            is HttpLogOperationStatus.Respond -> "Completed"
            HttpLogOperationStatus.NetworkFailed -> "Failed"
            HttpLogOperationStatus.Unsupported -> ""
        }
    }

    fun requestDateString(calendarProxy: CalendarProxy): String {
        return calendarProxy.initIfNeed(requestDateTimePresent).getFormatted()
    }

    fun responseDateString(calendarProxy: CalendarProxy): String {
        return calendarProxy.initIfNeed(responseDateTimePresent).getFormatted()
    }

    private fun formatBytes(bytes: Long): String {
        return HttpFormatUtils.formatByteCount(bytes, true)
    }

}