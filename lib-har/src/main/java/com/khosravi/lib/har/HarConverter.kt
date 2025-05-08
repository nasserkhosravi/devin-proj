package com.khosravi.lib.har

import org.json.JSONArray
import org.json.JSONObject

object HarConverter {

    //region toJson
    fun HarFile.toJson(): JSONObject {
        return JSONObject().apply {
            put("log", log.toJson())
        }
    }

    fun HarLog.toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("creator", creator.toJson())
            put("browser", browser?.toJson())
            put("pages", pages?.let { JSONArray().apply { it.forEach { page -> put(page.toJson()) } } })
            put("entries", JSONArray().apply { entries.forEach { entry -> put(entry.toJson()) } })
            put("comment", comment)
        }
    }

    fun HarCreator.toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("version", version)
            put("comment", comment)
        }
    }

    fun HarBrowser.toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("version", version)
            put("comment", comment)
        }
    }

    fun HarPage.toJson(): JSONObject {
        return JSONObject().apply {
            put("startedDateTime", startedDateTime)
            put("id", id)
            put("title", title)
            put("pageTimings", pageTimings.toJson())
            put("comment", comment)
        }
    }

    fun HarPageTimings.toJson(): JSONObject {
        return JSONObject().apply {
            put("onContentLoad", onContentLoad)
            put("onLoad", onLoad)
            put("comment", comment)
        }
    }

    fun HarEntry.toJson(): JSONObject {
        return JSONObject().apply {
            put("pageref", pageref)
            put("startedDateTime", startedDateTime)
            put("time", time)
            put("request", request.toJson())
            put("response", response?.toJson())
            putOpt("cache", cache?.toJson())
            putOpt("timings", timings?.toJson())
            put("serverIPAddress", serverIPAddress)
            put("connection", connection)
            put("comment", comment)
            put("custom", custom?.toJson())
        }
    }

    fun HarRequest.toJson(): JSONObject {
        return JSONObject().apply {
            put("method", method)
            put("url", url)
            put("httpVersion", httpVersion)
            put("cookies", JSONArray().apply { cookies.forEach { cookie -> put(cookie.toJson()) } })
            put("headers", JSONArray().apply { headers.forEach { header -> put(header.toJson()) } })
            put("queryString", JSONArray().apply { queryString.forEach { query -> put(query.toJson()) } })
            put("postData", postData?.toJson())
            put("headersSize", headersSize)
            put("bodySize", bodySize)
            put("comment", comment)
        }
    }

    fun HarResponse.toJson(): JSONObject {
        return JSONObject().apply {
            put("status", status)
            put("statusText", statusText)
            put("httpVersion", httpVersion)
            put("cookies", JSONArray().apply { cookies.forEach { cookie -> put(cookie.toJson()) } })
            put("headers", JSONArray().apply { headers.forEach { header -> put(header.toJson()) } })
            put("content", content.toJson())
            put("redirectURL", redirectURL)
            put("headersSize", headersSize)
            put("bodySize", bodySize)
            put("comment", comment)
        }
    }

    fun HarCookie.toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("value", value)
            put("path", path)
            put("domain", domain)
            put("expires", expires)
            put("httpOnly", httpOnly)
            put("secure", secure)
            put("comment", comment)
        }
    }

    fun HarHeader.toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("value", value)
            put("comment", comment)
        }
    }

    fun HarQueryString.toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("value", value)
            put("comment", comment)
        }
    }

    fun HarPostData.toJson(): JSONObject {
        return JSONObject().apply {
            put("mimeType", mimeType)
            put("params", JSONArray().apply { params.forEach { param -> put(param.toJson()) } })
            put("text", text)
            put("comment", comment)
        }
    }

    fun HarPostParam.toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("value", value)
            put("fileName", fileName)
            put("contentType", contentType)
            put("comment", comment)
        }
    }

    fun HarContent.toJson(): JSONObject {
        return JSONObject().apply {
            put("size", size)
            put("compression", compression)
            put("mimeType", mimeType)
            put("text", text)
            put("encoding", encoding)
            put("comment", comment)
        }
    }

    fun HarCache.toJson(): JSONObject {
        return JSONObject().apply {
            put("beforeRequest", beforeRequest?.toJson())
            put("afterRequest", afterRequest?.toJson())
            put("comment", comment)
        }
    }

    fun HarCacheEntry.toJson(): JSONObject {
        return JSONObject().apply {
            put("expires", expires)
            put("lastAccess", lastAccess)
            put("eTag", eTag)
            put("hitCount", hitCount)
            put("comment", comment)
        }
    }

    fun HarTimings.toJson(): JSONObject {
        return JSONObject().apply {
            put("blocked", blocked)
            put("dns", dns)
            put("connect", connect)
            put("send", send)
            put("wait", wait)
            put("receive", receive)
            put("ssl", ssl)
            put("comment", comment)
        }
    }

    fun HarEntryCustom.toJson(): JSONObject {
        return JSONObject().apply {
            put("responseTlsVersion", responseTlsVersion)
            put("responseCipherSuite", responseCipherSuite)
        }
    }

    //endregion


    //region json deserialize
    fun JSONObject.toHarFile(): HarFile {
        return HarFile(
            log = getJSONObject("log").toHarLog()
        )
    }

    fun JSONObject.toHarLog(): HarLog {
        return HarLog(
            version = getString("version"),
            creator = getJSONObject("creator").toHarCreator(),
            browser = optJSONObject("browser")?.toHarBrowser(),
            pages = optJSONArray("pages")?.let { jsonArray ->
                List(jsonArray.length()) { i -> jsonArray.getJSONObject(i).toHarPage() }
            },
            entries = getJSONArray("entries").let { jsonArray ->
                List(jsonArray.length()) { i -> jsonArray.getJSONObject(i).toHarEntry() }
            },
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarCreator(): HarCreator {
        return HarCreator(
            name = getString("name"),
            version = getString("version"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarBrowser(): HarBrowser {
        return HarBrowser(
            name = getString("name"),
            version = getString("version"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarPage(): HarPage {
        return HarPage(
            startedDateTime = getString("startedDateTime"),
            id = getString("id"),
            title = getString("title"),
            pageTimings = getJSONObject("pageTimings").toHarPageTimings(),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarPageTimings(): HarPageTimings {
        return HarPageTimings(
            onContentLoad = getLong("onContentLoad"),
            onLoad = getLong("onLoad"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarEntry(): HarEntry {
        return HarEntry(
            pageref = optStringOrNull("pageref"),
            startedDateTime = optString("startedDateTime", ""),
            time = optLong("time", -1),
            request = getJSONObject("request").toHarRequest(),
            response = optJSONObject("response")?.toHarResponse(),
            cache = optJSONObject("cache")?.toHarCache(),
            timings = optJSONObject("timings")?.toHarTimings(),
            serverIPAddress = optStringOrNull("serverIPAddress"),
            connection = optStringOrNull("connection"),
            comment = optStringOrNull("comment"),
            custom = optJSONObject("custom")?.toHarEntryCustom()
        )
    }

    fun JSONObject.toHarRequest(): HarRequest {
        return HarRequest(
            method = getString("method"),
            url = getString("url"),
            httpVersion = getString("httpVersion"),
            cookies = getJSONArray("cookies").let { jsonArray ->
                List(jsonArray.length()) { i -> jsonArray.getJSONObject(i).toHarCookie() }
            },
            headers = getJSONArray("headers").let { jsonArray ->
                List(jsonArray.length()) { i -> jsonArray.getJSONObject(i).toHarHeader() }
            },
            queryString = getJSONArray("queryString").let { jsonArray ->
                List(jsonArray.length()) { i -> jsonArray.getJSONObject(i).toHarQueryString() }
            },
            postData = optJSONObject("postData")?.toHarPostData(),
            headersSize = getLong("headersSize"),
            bodySize = getLong("bodySize"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarResponse(): HarResponse {
        return HarResponse(
            status = getInt("status"),
            statusText = getString("statusText"),
            httpVersion = getString("httpVersion"),
            cookies = getJSONArray("cookies").let { jsonArray ->
                List(jsonArray.length()) { i -> jsonArray.getJSONObject(i).toHarCookie() }
            },
            headers = getJSONArray("headers").let { jsonArray ->
                List(jsonArray.length()) { i -> jsonArray.getJSONObject(i).toHarHeader() }
            },
            content = getJSONObject("content").toHarContent(),
            redirectURL = getString("redirectURL"),
            headersSize = getLong("headersSize"),
            bodySize = getLong("bodySize"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarCookie(): HarCookie {
        return HarCookie(
            name = getString("name"),
            value = getString("value"),
            path = optStringOrNull("path"),
            domain = optStringOrNull("domain"),
            expires = optStringOrNull("expires"),
            httpOnly = optBooleanOrNull("httpOnly"),
            secure = optBooleanOrNull("secure"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarHeader(): HarHeader {
        return HarHeader(
            name = getString("name"),
            value = getString("value"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarQueryString(): HarQueryString {
        return HarQueryString(
            name = getString("name"),
            value = getString("value"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarPostData(): HarPostData {
        return HarPostData(
            mimeType = getString("mimeType"),
            params = optJSONArray("params")?.let { jsonArray ->
                List(jsonArray.length()) { i -> jsonArray.getJSONObject(i).toHarPostParam() }
            } ?: emptyList(),
            text = opt("text"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarPostParam(): HarPostParam {
        return HarPostParam(
            name = getString("name"),
            value = optStringOrNull("value"),
            fileName = optStringOrNull("fileName"),
            contentType = optStringOrNull("contentType"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarContent(): HarContent {
        return HarContent(
            size = getInt("size"),
            compression = optIntOrNull("compression"),
            mimeType = getString("mimeType"),
            text = opt("text"),
            encoding = optStringOrNull("encoding"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarCache(): HarCache {
        return HarCache(
            beforeRequest = optJSONObject("beforeRequest")?.toHarCacheEntry(),
            afterRequest = optJSONObject("afterRequest")?.toHarCacheEntry(),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarCacheEntry(): HarCacheEntry {
        return HarCacheEntry(
            expires = optStringOrNull("expires"),
            lastAccess = optStringOrNull("lastAccess"),
            eTag = optStringOrNull("eTag"),
            hitCount = getInt("hitCount"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarTimings(): HarTimings {
        return HarTimings(
            blocked = optLongOrNull("blocked"),
            dns = optLongOrNull("dns"),
            connect = optLongOrNull("connect"),
            send = getLong("send"),
            wait = getLong("wait"),
            receive = getLong("receive"),
            ssl = optLongOrNull("ssl"),
            comment = optStringOrNull("comment")
        )
    }

    fun JSONObject.toHarEntryCustom(): HarEntryCustom {
        return HarEntryCustom(
            responseTlsVersion = optStringOrNull("responseTlsVersion"),
            responseCipherSuite = optStringOrNull("responseCipherSuite"),
        )
    }
    //endregion

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (has(key)) {
            return getString(key)
        }
        return null
    }

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
        if (has(key)) {
            return getBoolean(key)
        }
        return null
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (has(key)) {
            return optInt(key)
        }
        return null
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (has(key)) {
            return optLong(key)
        }
        return null
    }

}
