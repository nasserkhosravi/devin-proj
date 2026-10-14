package com.khosravi.devin.present.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import com.khosravi.devin.present.data.http.HttpLogOperationStatus
import com.khosravi.devin.present.data.model.GetLogsQueryModel
import com.khosravi.devin.present.data.model.PageInfo
import com.khosravi.devin.present.present.http.GsonConverter
import com.khosravi.devin.present.testing.DevinDbFixture
import com.khosravi.devin.present.testing.ProviderFixture
import com.khosravi.devin.present.testing.ProviderFixture.onWorker
import com.khosravi.devin.read.DevinImageFlagsApi
import com.khosravi.devin.read.DevinUriHelper.OpStringParam
import com.khosravi.devin.read.DevinUriHelper.OpStringValue
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import com.khosravi.lib.har.HarContent
import com.khosravi.lib.har.HarCreator
import com.khosravi.lib.har.HarEntry
import com.khosravi.lib.har.HarFile
import com.khosravi.lib.har.HarLog
import com.khosravi.lib.har.HarRequest
import com.khosravi.lib.har.HarResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the presenter's read side against the real `DevinContentProvider` and Room DB. */
@RunWith(RobolectricTestRunner::class)
class ContentProviderLogsDaoTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ProviderFixture.register()
    }

    @After
    fun tearDown() {
        DevinDbFixture.resetSingleton()
    }

    private fun putLog(clientId: String, tag: String, value: String, date: Long, meta: JsonObject? = null, metaIndex: Pair<String, String>? = null) =
        ProviderFixture.putLog(context, clientId, tag, value, date, meta = meta, metaIndex = metaIndex)

    private fun query(clientId: String, model: GetLogsQueryModel?) =
        onWorker { ContentProviderLogsDao.queryLogList(context, clientId, model) }

    private fun model(
        tag: OpStringValue? = null,
        value: OpStringValue? = null,
        meta: OpStringParam? = null,
        timeLessThan: Long? = null,
        page: PageInfo? = null,
    ) = GetLogsQueryModel(null, tag, value, meta, timeLessThan, page)

    @Test
    fun `client list returns registered clients with their config`() {
        ProviderFixture.putClient(context, "app.a", JSONObject().put("logPassword", "1"))
        ProviderFixture.putClient(context, "app.b")

        val clients = onWorker { ClientContentProvider.getClientList(context) }.associateBy { it.id }

        assertEquals(setOf("app.a", "app.b"), clients.keys)
        assertEquals("1", clients.getValue("app.a").presenterConfig?.getString("logPassword"))
        assertNull(clients.getValue("app.b").presenterConfig)
    }

    @Test
    fun `getLog reads every column`() {
        val meta = JsonObject().apply { addProperty("k", "v") }
        val id = ProviderFixture.putLog(context, "app.a", "net", "GET /", 1_000, typeId = "http", meta = meta)

        val log = onWorker { ContentProviderLogsDao.getLog(context, id) }!!

        assertEquals(id, log.id)
        assertEquals("net", log.tag)
        assertEquals("GET /", log.value)
        assertEquals(1_000L, log.date)
        assertEquals(meta, log.meta)
        assertEquals("app.a", log.packageId)
        assertEquals("http", log.typeId)
    }

    @Test
    fun `getLog returns null for a missing id`() {
        assertNull(onWorker { ContentProviderLogsDao.getLog(context, 12345) })
    }

    @Test
    fun `log list is the client's logs, newest first`() {
        putLog("app.a", "t", "old", 1)
        putLog("app.a", "t", "new", 3)
        putLog("app.a", "t", "mid", 2)
        putLog("app.b", "t", "other", 4)

        assertEquals(listOf("new", "mid", "old"), query("app.a", null).map { it.value })
    }

    @Test
    fun `filters by tag operators`() {
        putLog("app.a", "network", "1", 1)
        putLog("app.a", "net", "2", 2)
        putLog("app.a", "ui-net", "3", 3)

        assertEquals(listOf("2"), query("app.a", model(tag = OpStringValue.EqualTo("net"))).map { it.value })
        assertEquals(listOf("2", "1"), query("app.a", model(tag = OpStringValue.StartWith("net"))).map { it.value })
        assertEquals(listOf("3", "2", "1"), query("app.a", model(tag = OpStringValue.Contain("net"))).map { it.value })
    }

    @Test
    fun `filters by value`() {
        putLog("app.a", "t", "login ok", 10)
        putLog("app.a", "t", "logout", 20)
        putLog("app.a", "t", "login failed", 30)

        assertEquals(
            listOf("login failed", "login ok"),
            query("app.a", model(value = OpStringValue.Contain("login"))).map { it.value },
        )
    }

    @Test
    fun `cursor query filters by time`() {
        putLog("app.a", "t", "a", 10)
        putLog("app.a", "t", "b", 20)
        putLog("app.a", "t", "c", 30)

        val values = onWorker {
            ContentProviderLogsDao.queryLogListAsCursor(context, "app.a", model(timeLessThan = 30))!!.use { cursor ->
                buildList { while (cursor.moveToNext()) add(ContentProviderLogsDao.readLogModel(cursor).value) }
            }
        }

        assertEquals(listOf("b", "a"), values)
    }

    @Test
    fun `filters by meta index`() {
        putLog("app.a", "net", "a", 1, metaIndex = "url" to "https://api.example.com/users")
        putLog("app.a", "net", "b", 2, metaIndex = "url" to "https://cdn.example.com/img")
        putLog("app.a", "net", "c", 3)

        val result = query("app.a", model(meta = OpStringParam("url", OpStringValue.Contain("api."))))

        assertEquals(listOf("a"), result.map { it.value })
    }

    @Test
    fun `pages through results`() {
        (1..5).forEach { putLog("app.a", "t", "v$it", it.toLong()) }

        assertEquals(listOf("v5", "v4"), query("app.a", model(page = PageInfo(index = 0, count = 2))).map { it.value })
        assertEquals(listOf("v3", "v2"), query("app.a", model(page = PageInfo(index = 1, count = 2))).map { it.value })
        assertEquals(listOf("v1"), query("app.a", model(page = PageInfo(index = 2, count = 2))).map { it.value })
    }

    @Test
    fun `getAllTags returns distinct tags of the client`() {
        putLog("app.a", "net", "1", 1)
        putLog("app.a", "net", "2", 2)
        putLog("app.a", "ui", "3", 3)
        putLog("app.b", "db", "4", 4)

        assertEquals(setOf("net", "ui"), onWorker { ContentProviderLogsDao.getAllTags(context, "app.a") })
    }

    @Test
    fun `clear deletes only that client's logs`() {
        putLog("app.a", "t", "1", 1)
        putLog("app.b", "t", "2", 2)

        onWorker { ContentProviderLogsDao.clear(context, "app.a") }

        assertTrue(query("app.a", null).isEmpty())
        assertEquals(1, query("app.b", null).size)
    }

    @Test
    fun `http log is parsed from its HAR meta`() {
        val id = putLog("app.a", DevinHttpFlagsApi.LOG_TAG, "GET users", 5, meta = httpMeta(status = 201, error = "boom"))

        val detail = onWorker { ContentProviderLogsDao.getHttpLog(context, "app.a", LogId(id)) }!!

        assertEquals(id, detail.logId)
        assertEquals("Completed", detail.statusString())
        assertEquals("201 ", detail.responseSummaryText)
        assertEquals("https://api.example.com/users", detail.urlExternalForm)
        assertTrue(detail.isSsl)
        assertEquals("GET", detail.harRequest.method)
        assertEquals("boom", detail.summeryOfError)
    }

    @Test
    fun `http log of another client or without meta is null`() {
        val id = putLog("app.a", "net", "x", 5, meta = httpMeta())
        val noMeta = putLog("app.a", "net", "y", 6)

        assertNull(onWorker { ContentProviderLogsDao.getHttpLog(context, "app.b", LogId(id)) })
        assertNull(onWorker { ContentProviderLogsDao.getHttpLog(context, "app.a", LogId(noMeta)) })
    }

    @Test
    fun `mapToHttpModel builds the list model`() {
        val id = putLog("app.a", "net", "GET users", 5, meta = httpMeta(status = 404))
        val log = onWorker { ContentProviderLogsDao.getLog(context, id) }!!

        val model = ContentProviderLogsDao.mapToHttpModel(log)!!

        assertEquals(LogId(id), model.logId)
        assertEquals("GET users", model.name)
        assertEquals("https://api.example.com/users", model.url)
        assertEquals(HttpLogOperationStatus.Respond(404), model.operationStatus)
        assertEquals("GET", model.httpMethod)
    }

    @Test
    fun `mapToHttpModel returns null for non-http meta`() {
        val log = LogData(1, "t", "v", 1, JsonObject().apply { addProperty("x", 1) }, "app.a", null)
        assertNull(ContentProviderLogsDao.mapToHttpModel(log))
        assertNull(ContentProviderLogsDao.mapToHttpModel(LogData(1, "t", "v", 1, null, "app.a", null)))
    }

    @Test
    fun `mapToImageModel reads url and status`() {
        val meta = JsonObject().apply {
            addProperty(DevinImageFlagsApi.KEY_IMAGE_URL, "https://img/1.png")
            addProperty(DevinImageFlagsApi.KEY_IMAGE_STATUS, DevinImageFlagsApi.Status.FAILED)
        }

        val image = ContentProviderLogsDao.mapToImageModel(LogData(1, "image", "avatar", 9, meta, "app.a", null))

        assertEquals(ImageLogData("avatar", "https://img/1.png", DevinImageFlagsApi.Status.FAILED, 9), image)
        assertTrue(image.isFailed())
    }

    private fun httpMeta(status: Int = 200, error: String? = null): JsonObject {
        val url = "https://api.example.com/users"
        val har = HarFile(
            HarLog(
                version = HarLog.HAR_SCHEME_VERSION,
                creator = HarCreator("test", "1", null),
                browser = null,
                pages = null,
                entries = listOf(
                    HarEntry(
                        pageref = null,
                        startedDateTime = "2026-10-14T00:00:00Z",
                        request = HarRequest("GET", url, "HTTP/1.1", emptyList(), emptyList(), emptyList(), null, comment = null),
                        response = HarResponse(
                            status, "", "HTTP/1.1", emptyList(), emptyList(),
                            HarContent(0, null, "text/plain", "", null, null), "", comment = null,
                        ),
                        cache = null,
                        timings = null,
                        serverIPAddress = null,
                        connection = null,
                        comment = null,
                        custom = null,
                    )
                ),
                comment = null,
            )
        )
        return JsonObject().apply {
            addProperty(DevinHttpFlagsApi.KEY_URL, url)
            addProperty(DevinHttpFlagsApi.KEY_STATUS_TYPE, DevinHttpFlagsApi.Status.HTTP_CODE)
            add(DevinHttpFlagsApi.KEY_HAR, GsonConverter.instance.toJsonTree(har))
            error?.let { addProperty(DevinHttpFlagsApi.KEY_SUMMERY_OF_ERROR, it) }
        }
    }
}
