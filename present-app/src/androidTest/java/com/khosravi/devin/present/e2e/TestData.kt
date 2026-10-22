package com.khosravi.devin.present.e2e

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.CalendarType
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.present.http.GsonConverter
import com.khosravi.devin.read.DevinImageFlagsApi
import com.khosravi.devin.read.DevinLogFlagsApi
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.DevinContentProvider
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import com.khosravi.devin.write.room.DevinDB
import com.khosravi.devin.write.room.LogTable
import com.khosravi.lib.har.HarContent
import com.khosravi.lib.har.HarCreator
import com.khosravi.lib.har.HarEntry
import com.khosravi.lib.har.HarFile
import com.khosravi.lib.har.HarHeader
import com.khosravi.lib.har.HarLog
import com.khosravi.lib.har.HarPostData
import com.khosravi.lib.har.HarRequest
import com.khosravi.lib.har.HarResponse
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Seeds data through the app's own content provider, exactly as client apps (and the OkHttp interceptor) do.
 * Dates are recent (inside the default 7-day retention) and end in `.123` ms so times are assertable.
 */
object TestData {

    const val CLIENT = "e2e.client"
    const val CLIENT_B = "e2e.client.b"

    private val context: Context get() = AppState.context

    // region time
    /** A timestamp [secondsAgo] before now, on today's date if possible, with millis fixed to [millis]. */
    fun ts(secondsAgo: Long = 60, millis: Int = 123): Long {
        val now = System.currentTimeMillis()
        val startOfDay = startOfDay(now)
        val seconds = ((now - startOfDay) / 1000).coerceAtLeast(secondsAgo + 1)
        return startOfDay + (seconds - secondsAgo) * 1000 + millis
    }

    /** Noon-ish of the day [days] before today, with millis fixed to [millis]. */
    fun daysAgo(days: Int, millis: Int = 123): Long =
        startOfDay(System.currentTimeMillis()) - TimeUnit.DAYS.toMillis(days.toLong()) + TimeUnit.HOURS.toMillis(12) + millis

    private fun startOfDay(now: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun timeWithMillis(date: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(date))
    fun time(date: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(date))

    /** The date header text the app shows (Persian calendar, `yyyy-M-d`), computed with the app's own calendar. */
    fun persianDate(date: Long): String = CalendarProxy(CalendarType.PERSIAN).initIfNeed(DatePresent(date)).getFormatted()
    // endregion

    // region clients
    fun client(id: String = CLIENT, presenterConfig: JSONObject? = null) {
        context.contentResolver.insert(
            DevinUriHelper.getClientListUri(),
            DevinContentProvider.contentValuePutClient(id, presenterConfig),
        )
    }

    fun passwordConfig(password: String) = JSONObject().put("logPassword", password)

    fun notificationConfig(groupName: String, vararg tags: String) = JSONObject().put(
        "logNotifications",
        JSONObject().put("enabled", true).put(
            "groups",
            JSONArray().put(JSONObject().put("name", groupName).put("tags", JSONArray(tags.toList())).put("color", "#FF0000")),
        ),
    )
    // endregion

    // region logs
    fun log(
        tag: String,
        value: String,
        date: Long = ts(),
        clientId: String = CLIENT,
        level: Int? = null,
        meta: JsonObject? = null,
        typeId: String? = null,
        metaIndex: Pair<String, String>? = null,
    ): Long {
        val fullMeta = when {
            meta != null -> meta
            level != null -> JsonObject().apply { addProperty(DevinLogFlagsApi.KEY_LOG_LEVEL, level) }
            else -> null
        }
        val uri = context.contentResolver.insert(
            DevinUriHelper.getLogListUri(),
            DevinContentProvider.contentValueLog(
                appId = clientId,
                tag = tag,
                value = value,
                typeId = typeId,
                meta = fullMeta?.toString(),
                content = null,
                metaIndexPair = metaIndex,
                date = Date(date),
            ),
        ) ?: error("insert failed")
        return uri.lastPathSegment!!.toLong()
    }

    fun sessionStart(version: String, date: Long = ts(), clientId: String = CLIENT): Long = log(
        tag = DevinLogFlagsApi.TAG_SESSION_START,
        value = "Session started",
        date = date,
        clientId = clientId,
        meta = JsonObject().apply { addProperty("payload", "App Version: $version (45)") },
    )

    fun image(name: String, url: String, status: Int, date: Long = ts(), clientId: String = CLIENT): Long = log(
        tag = DevinImageFlagsApi.LOG_TAG,
        value = name,
        date = date,
        clientId = clientId,
        typeId = DevinImageFlagsApi.TYPE_ID,
        meta = JsonObject().apply {
            addProperty(DevinImageFlagsApi.KEY_IMAGE_URL, url)
            addProperty(DevinImageFlagsApi.KEY_IMAGE_STATUS, status)
        },
    )

    class Http(
        val url: String = "https://api.example.com/v1/users?page=2",
        val method: String = "POST",
        val status: Int = 201,
        val statusText: String = "Created",
        val requestHeaders: List<Pair<String, String>> = listOf("Content-Type" to "application/json", "X-Trace" to "abc"),
        val requestBody: String? = """{"name":"neo","role":"admin"}""",
        val responseHeaders: List<Pair<String, String>> = listOf("Content-Type" to "application/json"),
        val responseBody: String? = """{"id":7,"name":"neo","tags":["a","b"],"name2":"neo"}""",
        val durationMs: Long = 42,
        val statusType: Int = DevinHttpFlagsApi.Status.HTTP_CODE,
    )

    /** An HTTP log with a HAR meta blob and the `url` meta index, like `devin-write-okhttp` writes it. */
    fun http(spec: Http = Http(), date: Long = ts(), clientId: String = CLIENT): Long {
        val path = java.net.URL(spec.url).path
        val har = HarFile(
            HarLog(
                version = HarLog.HAR_SCHEME_VERSION,
                creator = HarCreator("e2e", "1", null),
                browser = null,
                pages = null,
                entries = listOf(
                    HarEntry(
                        pageref = null,
                        startedDateTime = Instant.ofEpochMilli(date).toString(),
                        time = spec.durationMs,
                        request = HarRequest(
                            method = spec.method,
                            url = spec.url,
                            httpVersion = "HTTP/1.1",
                            cookies = emptyList(),
                            headers = spec.requestHeaders.map { HarHeader(it.first, it.second, null) },
                            queryString = emptyList(),
                            postData = spec.requestBody?.let { HarPostData("application/json", emptyList(), JsonParser.parseString(it), null) },
                            headersSize = 40,
                            bodySize = spec.requestBody?.length?.toLong() ?: 0,
                            comment = null,
                        ),
                        response = HarResponse(
                            status = spec.status,
                            statusText = spec.statusText,
                            httpVersion = "HTTP/1.1",
                            cookies = emptyList(),
                            headers = spec.responseHeaders.map { HarHeader(it.first, it.second, null) },
                            // JSON bodies are stored parsed, like devin-write-okhttp's HarMapper does.
                            content = HarContent(spec.responseBody?.length ?: 0, null, "application/json", spec.responseBody?.let(JsonParser::parseString), null, null),
                            redirectURL = "",
                            headersSize = 30,
                            bodySize = spec.responseBody?.length?.toLong() ?: 0,
                            comment = null,
                        ),
                        cache = null,
                        timings = null,
                        serverIPAddress = null,
                        connection = null,
                        comment = null,
                        custom = null,
                    ),
                ),
                comment = null,
            ),
        )
        val meta = JsonObject().apply {
            addProperty(DevinLogFlagsApi.KEY_META_TYPE, DevinHttpFlagsApi.TYPE_ID)
            addProperty(DevinLogFlagsApi.KEY_LOG_LEVEL, if (spec.status in 400..600) Log.ERROR else Log.INFO)
            addProperty(DevinHttpFlagsApi.KEY_STATUS_TYPE, spec.statusType)
            addProperty(DevinHttpFlagsApi.KEY_URL, spec.url)
            add(DevinHttpFlagsApi.KEY_HAR, GsonConverter.instance.toJsonTree(har))
        }
        return log(
            tag = DevinHttpFlagsApi.LOG_TAG,
            value = "${spec.status} ${spec.method} $path",
            date = date,
            clientId = clientId,
            meta = meta,
            typeId = DevinHttpFlagsApi.TYPE_ID,
            metaIndex = DevinHttpFlagsApi.KEY_URL to spec.url,
        )
    }

    /** Many logs in one go (each still goes through the provider). */
    fun logs(count: Int, tag: String, clientId: String = CLIENT, value: (Int) -> String): List<Long> {
        val base = ts(secondsAgo = count.toLong() + 120)
        return (0 until count).map { i -> log(tag = tag, value = value(i), date = base + i * 1000L, clientId = clientId) }
    }
    // endregion

    // region reads
    fun logCount(clientId: String = CLIENT): Long =
        DevinDB.getInstance(context).openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM ${LogTable.TABLE_NAME} WHERE ${LogTable.COLUMN_CLIENT_ID} = ?", arrayOf(clientId))
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }
    // endregion
}
