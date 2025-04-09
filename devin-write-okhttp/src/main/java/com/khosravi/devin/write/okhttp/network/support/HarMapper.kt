package com.khosravi.devin.write.okhttp.network.support

import com.khosravi.devin.write.BuildConfig
import com.khosravi.devin.write.okhttp.network.entity.HttpRequestModel
import com.khosravi.devin.write.okhttp.network.entity.HttpResponseModel
import com.khosravi.lib.har.HarCache
import com.khosravi.lib.har.HarContent
import com.khosravi.lib.har.HarCreator
import com.khosravi.lib.har.HarEntryCustom
import com.khosravi.lib.har.HarEntry
import com.khosravi.lib.har.HarFile
import com.khosravi.lib.har.HarHeader
import com.khosravi.lib.har.HarLog
import com.khosravi.lib.har.HarPostData
import com.khosravi.lib.har.HarQueryString
import com.khosravi.lib.har.HarRequest
import com.khosravi.lib.har.HarResponse
import com.khosravi.lib.har.HarTimings
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object HarMapper {

    private const val VALUE_MAPPER_VERSION = "1.0"
    private const val VALUE_CREATOR_NAME = "devin_write_okhttp"
    private const val META_KEY_DEVIN_WRITE_VERSION_NAME = "devin_write_version"
    private const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    fun from(url: URL, request: HttpRequestModel, responseModel: HttpResponseModel?, tookMSFallback: Long = -1): HarFile {
        val harRequest = HarRequest(
            method = request.method,
            url = url.toString(),
            httpVersion = responseModel?.protocol ?: "",
            headers = request.getHeadersAsList().map { HarHeader(it.name, it.value, null) },
            cookies = emptyList(),
            queryString = url.getQueryParameters().map {
                HarQueryString(it.first, it.second, null)
            },
            postData = capturePostData(request.requestBody, request.requestContentType),
            headersSize = request.requestHeadersSize ?: -1,
            bodySize = request.requestContentSize ?: -1,
            comment = null
        )
        var entryCustom: HarEntryCustom? = null
        val harResponse = responseModel?.let {
            if (responseModel.responseCipherSuite != null || responseModel.responseTlsVersion != null) {
                entryCustom = HarEntryCustom(
                    responseCipherSuite = responseModel.responseCipherSuite,
                    responseTlsVersion = responseModel.responseTlsVersion
                )
            }

            // Capture Response Data
            HarResponse(
                status = responseModel.responseCode,
                statusText = responseModel.responseMessage,
                httpVersion = responseModel.protocol,
                headers = responseModel.getHeadersAsList().map { HarHeader(it.name, it.value, null) },
                cookies = emptyList(),
                content = HarContent(
                    size = 0,
                    compression = null,
                    mimeType = responseModel.responseContentType ?: "",
                    text = responseModel.decodedBody ?: "",
                    encoding = null,
                    comment = null
                ),
                redirectURL = "",
                headersSize = responseModel.responseHeadersSize,
                bodySize = responseModel.getPreferredResponseBodySize(),
                comment = null
            )

        }


        val harEntry = HarEntry(
            pageref = null,
            startedDateTime = formatStartedDateTime(request.requestDate),
            time = responseModel?.tookMs ?: tookMSFallback,
            request = harRequest,
            response = harResponse,
            cache = HarCache(null, null, null),
            timings = HarTimings(null, null, null, 0, responseModel?.tookMs ?: tookMSFallback, 0, null, null),
            serverIPAddress = responseModel?.serverIpAddress,
            connection = responseModel?.connection,
            comment = null,
            custom = entryCustom
        )

        return toHarFile(harEntry)
    }

    private fun formatStartedDateTime(dateValue: Long): String {
        return SimpleDateFormat(DATE_FORMAT, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(dateValue))
    }

    private fun capturePostData(data: String?, contentType: String?): HarPostData {
        return HarPostData(
            mimeType = contentType ?: "application/octet-stream",
            params = emptyList(),
            text = data ?: "",
            comment = null
        )
    }

    private fun toHarFile(harEntry: HarEntry): HarFile {
        return HarFile(
            HarLog(
                version = HarLog.HAR_SCHEME_VERSION,
                creator = HarCreator(
                    VALUE_CREATOR_NAME, VALUE_MAPPER_VERSION, JSONObject()
                        .put(META_KEY_DEVIN_WRITE_VERSION_NAME, BuildConfig.DEVIN_WRITE_VERSION)
                        .toString()
                ),
                browser = null,
                pages = null,
                entries = listOf(harEntry),
                comment = null
            )
        )
    }
}