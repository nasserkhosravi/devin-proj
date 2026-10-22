package com.khosravi.devin.write

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.api.DevinLogCore
import com.khosravi.devin.write.testing.DevinTestFixture
import com.khosravi.devin.write.testing.DevinTestFixture.onWorker
import com.khosravi.devin.write.testing.DevinTestFixture.toLogRows
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class DevinContentProviderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver: ContentResolver get() = context.contentResolver
    private lateinit var provider: DevinContentProvider

    @Before
    fun setUp() {
        DevinTestFixture.resetSingletons()
        provider = DevinTestFixture.registerProvider()
    }

    @After
    fun tearDown() {
        DevinTestFixture.resetSingletons()
    }

    private fun insertLog(
        clientId: String = "client",
        tag: String = "tag",
        value: String = "value",
        date: Long = 1,
        typeId: String? = null,
        meta: String? = null,
        content: ByteArray? = null,
        metaIndex: Pair<String, String>? = null,
    ): Uri = onWorker {
        resolver.insert(
            DevinUriHelper.getLogListUri(),
            DevinContentProvider.contentValueLog(clientId, tag, value, typeId, meta, content, metaIndex, Date(date)),
        )!!
    }

    private fun Uri.id() = lastPathSegment!!.toLong()

    private fun rawQuery(
        uri: Uri,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
    ) = onWorker {
        resolver.query(uri, projection, selection, selectionArgs, sortOrder)!!.use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("_value"))) }
        }
    }

    @Test
    fun `insert log stores every field and returns its uri`() {
        val uri = insertLog(clientId = "client", tag = "tag", value = "value", date = 42, typeId = "type", meta = "{}")

        assertEquals("content://com.khosravi.devin.provider/log/${uri.id()}", uri.toString())
        val row = DevinTestFixture.logs(context, "client").single()
        assertEquals(uri.id(), row.id)
        assertEquals("tag", row.tag)
        assertEquals("value", row.value)
        assertEquals(42L, row.date)
        assertEquals("type", row.typeId)
        assertEquals("{}", row.meta)
    }

    @Test
    fun `insert log notifies its change uri`() {
        val id = insertLog(clientId = "client", tag = "tag").id()

        val notified = shadowOf(resolver).notifiedUris.map { it.uri }
        assertTrue(notified.contains(DevinUriHelper.getLogChangeUri(id, "client", "tag")))
    }

    @Test
    fun `insert log with a meta index stores it`() {
        val id = insertLog(metaIndex = "url" to "https://x/api").id()

        assertEquals(listOf("url" to "https://x/api"), DevinTestFixture.metaIndexes(context, id))
    }

    @Test
    fun `insert log without a meta index stores none`() {
        val id = insertLog().id()

        assertEquals(emptyList<Pair<String, String>>(), DevinTestFixture.metaIndexes(context, id))
    }

    @Test
    fun `insert log with content writes it to a file`() {
        val bytes = byteArrayOf(1, 2, 3)

        val id = insertLog(clientId = "client", content = bytes).id()

        val file = File(context.filesDir, "client/$id/file_content")
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `query by log id returns that log`() {
        insertLog(value = "other")
        val id = insertLog(value = "wanted").id()

        val rows = onWorker { resolver.query(DevinUriHelper.getLogUri(id), null, null, null, null)!!.use { it.toLogRows() } }

        assertEquals(listOf("wanted"), rows.map { it.value })
    }

    @Test
    fun `query by a non-numeric log id returns null`() {
        val uri = Uri.parse("content://com.khosravi.devin.provider/log?_id=abc")

        assertNull(onWorker { resolver.query(uri, null, null, null, null) })
    }

    @Test
    fun `query by client returns its logs newest first`() {
        insertLog(clientId = "a", value = "a1", date = 1)
        insertLog(clientId = "b", value = "b1", date = 2)
        insertLog(clientId = "a", value = "a2", date = 3)

        assertEquals(listOf("a2", "a1"), DevinTestFixture.logs(context, "a").map { it.value })
    }

    @Test
    fun `query by client and tag filters by tag`() {
        insertLog(clientId = "a", tag = "x", value = "ax")
        insertLog(clientId = "a", tag = "y", value = "ay")
        insertLog(clientId = "b", tag = "x", value = "bx")

        val rows = onWorker {
            resolver.query(DevinUriHelper.getLogListUri("a", tag = "x"), null, null, null, null)!!.use { it.toLogRows() }
        }

        assertEquals(listOf("ax"), rows.map { it.value })
    }

    @Test
    fun `query logs without a client returns null`() {
        assertNull(onWorker { resolver.query(DevinUriHelper.getLogListUri(), null, null, null, null) })
    }

    @Test
    fun `raw query applies projection, selection, order and paging`() {
        (1L..5L).forEach { insertLog(clientId = "a", value = "v$it", date = it) }
        insertLog(clientId = "b", value = "other", date = 6)

        val values = rawQuery(
            DevinUriHelper.getLogListUri("a", pageIndex = 1, itemCount = 2, isRawQuery = true),
            projection = arrayOf("_value"),
            selection = "_clientId = ?",
            selectionArgs = arrayOf("a"),
            sortOrder = "_date ASC",
        )

        assertEquals(listOf("v3", "v4"), values)
    }

    @Test
    fun `raw query meta search Contain combines with selection`() {
        insertLog(clientId = "a", value = "match", metaIndex = "url" to "https://x/api/users")
        insertLog(clientId = "a", value = "miss", metaIndex = "url" to "https://x/home")
        insertLog(clientId = "b", value = "other-client", metaIndex = "url" to "https://x/api")

        val values = rawQuery(
            DevinUriHelper.getLogListUri(
                "a",
                msl1 = DevinUriHelper.OpStringParam("url", DevinUriHelper.OpStringValue.Contain("api")),
                isRawQuery = true,
            ),
            selection = "_clientId = ?",
            selectionArgs = arrayOf("a"),
        )

        assertEquals(listOf("match"), values)
    }

    @Test
    fun `raw query meta search works without a selection`() {
        insertLog(value = "match", metaIndex = "url" to "https://x/api")
        insertLog(value = "miss", metaIndex = "url" to "https://x/home")

        val values = rawQuery(
            DevinUriHelper.getLogListUri(
                "client",
                msl1 = DevinUriHelper.OpStringParam("url", DevinUriHelper.OpStringValue.Contain("api")),
                isRawQuery = true,
            ),
        )

        assertEquals(listOf("match"), values)
    }

    @Test
    fun `raw query meta search StartWith matches prefixes only`() {
        insertLog(value = "prefix", metaIndex = "url" to "api/users")
        insertLog(value = "middle", metaIndex = "url" to "v1/api")

        val values = rawQuery(
            DevinUriHelper.getLogListUri(
                "client",
                msl1 = DevinUriHelper.OpStringParam("url", DevinUriHelper.OpStringValue.StartWith("api")),
                isRawQuery = true,
            ),
            selection = "_clientId = ?",
            selectionArgs = arrayOf("client"),
        )

        assertEquals(listOf("prefix"), values)
    }

    @Test
    fun `raw query meta search EqualTo matches whole values only`() {
        insertLog(value = "exact", metaIndex = "url" to "it's/api")
        insertLog(value = "longer", metaIndex = "url" to "it's/api/users")

        val values = rawQuery(
            DevinUriHelper.getLogListUri(
                "client",
                msl1 = DevinUriHelper.OpStringParam("url", DevinUriHelper.OpStringValue.EqualTo("it's/api")),
                isRawQuery = true,
            ),
        )

        assertEquals(listOf("exact"), values)
    }

    @Test
    fun `update rewrites the log and notifies`() {
        val uri = insertLog(clientId = "client", tag = "old", value = "old")
        shadowOf(resolver).notifiedUris.clear()

        val result = onWorker {
            resolver.update(
                uri,
                DevinContentProvider.contentValueLog("client", "new", "new", "type", "{}", null, null),
                null,
                null,
            )
        }

        assertEquals(DevinLogCore.FLAG_OPERATION_SUCCESS, result)
        val row = DevinTestFixture.logs(context, "client").single()
        assertEquals("new", row.tag)
        assertEquals("new", row.value)
        assertEquals("type", row.typeId)
        val notified = shadowOf(resolver).notifiedUris.map { it.uri }
        assertTrue(notified.contains(DevinUriHelper.getLogChangeUri(uri.id(), "client", "new")))
    }

    @Test
    fun `update with a non-numeric id fails`() {
        val result = onWorker {
            resolver.update(
                Uri.parse("content://com.khosravi.devin.provider/log/abc"),
                DevinContentProvider.contentValueLog("client", "tag", "value", null, null, null, null),
                null,
                null,
            )
        }

        assertEquals(DevinLogCore.FLAG_OPERATION_FAILED, result)
    }

    @Test
    fun `delete logs removes only that client`() {
        insertLog(clientId = "a")
        insertLog(clientId = "b")

        val result = onWorker { resolver.delete(DevinUriHelper.getLogListUri("a"), null, null) }

        assertEquals(DevinLogCore.FLAG_OPERATION_SUCCESS, result)
        assertEquals(0, DevinTestFixture.logs(context, "a").size)
        assertEquals(1, DevinTestFixture.logs(context, "b").size)
    }

    @Test
    fun `delete logs without a client fails cleanly`() {
        val result = onWorker { resolver.delete(DevinUriHelper.getLogListUri(), null, null) }

        assertEquals(DevinLogCore.FLAG_OPERATION_FAILED, result)
    }

    @Test
    fun `put client stores package and presenter config`() {
        val uri = onWorker {
            resolver.insert(
                DevinUriHelper.getClientListUri(),
                DevinContentProvider.contentValuePutClient("pkg", JSONObject().put("k", "v")),
            )
        }

        assertEquals(listOf("client"), uri!!.pathSegments.dropLast(1))
        val (packageId, config) = DevinTestFixture.clients(context).single()
        assertEquals("pkg", packageId)
        assertEquals("v", JSONObject(config!!).getString("k"))
    }

    @Test
    fun `put client without config stores null`() {
        onWorker { resolver.insert(DevinUriHelper.getClientListUri(), DevinContentProvider.contentValuePutClient("pkg", null)) }

        assertEquals(listOf("pkg" to null), DevinTestFixture.clients(context))
    }

    @Test
    fun `delete clients removes all of them`() {
        onWorker {
            resolver.insert(DevinUriHelper.getClientListUri(), DevinContentProvider.contentValuePutClient("a", null))
            resolver.insert(DevinUriHelper.getClientListUri(), DevinContentProvider.contentValuePutClient("b", null))
        }

        val result = onWorker { resolver.delete(DevinUriHelper.getClientListUri(), null, null) }

        assertEquals(DevinLogCore.FLAG_OPERATION_SUCCESS, result)
        assertEquals(emptyList<Pair<String, String?>>(), DevinTestFixture.clients(context))
    }

    @Test
    fun `uris outside its authority or tables are rejected`() {
        val foreign = Uri.parse("content://other.authority/log?_clientId=client")
        val unknownPath = Uri.parse("content://com.khosravi.devin.provider/unknown")
        val values = DevinContentProvider.contentValueLog("client", "tag", "value", null, null, null, null)

        onWorker {
            for (uri in listOf(foreign, unknownPath)) {
                assertNull(provider.query(uri, null, null, null, null))
                assertNull(provider.insert(uri, values))
                assertEquals(DevinLogCore.FLAG_OPERATION_FAILED, provider.update(uri, values, null, null))
                assertEquals(DevinLogCore.FLAG_OPERATION_FAILED, provider.delete(uri, null, null))
            }
        }
        assertEquals(0, DevinTestFixture.logs(context, "client").size)
    }
}
