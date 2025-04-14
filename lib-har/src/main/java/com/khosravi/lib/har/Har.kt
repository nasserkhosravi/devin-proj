package com.khosravi.lib.har

// https://github.com/ahmadnassri/har-spec/blob/master/versions/1.2.md#log
data class HarFile(
    val log: HarLog,
)

data class HarLog(
    val version: String,
    val creator: HarCreator,
    val browser: HarBrowser?,
    val pages: List<HarPage>?,
    val entries: List<HarEntry>,
    val comment: String?,
) {
    companion object {
        const val HAR_SCHEME_VERSION = "1.2"
    }
}

data class HarCreator(
    val name: String,
    val version: String,
    val comment: String?,
)

data class HarBrowser(
    val name: String,
    val version: String,
    val comment: String?,
)

data class HarPage(
    val startedDateTime: String,
    val id: String,
    val title: String,
    val pageTimings: HarPageTimings,
    val comment: String?,
)

data class HarPageTimings(
    val onContentLoad: Long = -1,
    val onLoad: Long = -1,
    val comment: String?,
)

data class HarEntry(
    val pageref: String?,
    val startedDateTime: String,
    val time: Long = 0,//elapsed time
    val request: HarRequest,
    val response: HarResponse?,
    val cache: HarCache?,
    val timings: HarTimings?,
    val serverIPAddress: String?,
    val connection: String?,
    val comment: String?,
    val custom: HarEntryCustom?,
)

data class HarRequest(
    val method: String,
    val url: String,
    val httpVersion: String,
    val cookies: List<HarCookie>,
    val headers: List<HarHeader>,
    val queryString: List<HarQueryString>,
    val postData: HarPostData?,
    val headersSize: Long = -1,
    val bodySize: Long = -1,
    val comment: String?,
)

data class HarResponse(
    val status: Int,
    val statusText: String,
    val httpVersion: String,
    val cookies: List<HarCookie>,
    val headers: List<HarHeader>,
    val content: HarContent,
    val redirectURL: String,
    val headersSize: Long = -1,
    val bodySize: Long = -1,
    val comment: String?,
)

data class HarCookie(
    val name: String,
    val value: String,
    val path: String?,
    val domain: String?,
    val expires: String?,
    val httpOnly: Boolean?,
    val secure: Boolean?,
    val comment: String?,
)

data class HarHeader(
    val name: String,
    val value: String,
    val comment: String?,
)

data class HarQueryString(
    val name: String,
    val value: String,
    val comment: String?,
)

data class HarPostData(
    val mimeType: String,
    val params: List<HarPostParam>,
    val text: String,
    val comment: String?,
)

data class HarPostParam(
    val name: String,
    val value: String?,
    val fileName: String?,
    val contentType: String?,
    val comment: String?,
)

data class HarContent(
    val size: Int,
    val compression: Int?,
    val mimeType: String,
    val text: String?,
    val encoding: String?,
    val comment: String?,
)

data class HarCache(
    val beforeRequest: HarCacheEntry?,
    val afterRequest: HarCacheEntry?,
    val comment: String?,
)

data class HarCacheEntry(
    val expires: String?,
    val lastAccess: String?,
    val eTag: String?,
    val hitCount: Int,
    val comment: String?,
)

data class HarTimings(
    val blocked: Long?,
    val dns: Long?,
    val connect: Long?,
    val send: Long,
    val wait: Long,
    val receive: Long,
    val ssl: Long?,
    val comment: String?,
)

data class HarEntryCustom(
    val responseCipherSuite: String?,
    val responseTlsVersion: String?,
)