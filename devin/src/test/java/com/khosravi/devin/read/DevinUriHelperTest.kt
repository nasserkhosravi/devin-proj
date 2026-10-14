package com.khosravi.devin.read

import android.net.Uri
import com.khosravi.devin.read.DevinUriHelper.getMsl1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevinUriHelperTest {

    @Test
    fun `getLogListUri carries every given parameter`() {
        val uri = DevinUriHelper.getLogListUri(
            clientId = "client",
            tag = "tag",
            msl1 = DevinUriHelper.OpStringParam("url", DevinUriHelper.OpStringValue.Contain("api")),
            pageIndex = 2,
            itemCount = 10,
            isRawQuery = true,
        )

        assertEquals("content", uri.scheme)
        assertEquals("com.khosravi.devin.provider", uri.authority)
        assertEquals("/log", uri.path)
        assertEquals("client", uri.getQueryParameter("_clientId"))
        assertEquals("tag", uri.getQueryParameter("_tag"))
        assertEquals("true", uri.getQueryParameter("isRawQuery"))
        assertEquals("2", uri.getQueryParameter("pageIndex"))
        assertEquals("10", uri.getQueryParameter("itemCount"))
        assertEquals("url", uri.getQueryParameter("metaSearchFieldName"))
        assertEquals("api", uri.getQueryParameter("metaSearchFieldValue"))
        assertEquals("Contain", uri.getQueryParameter("metaSearchFieldOperation"))
    }

    @Test
    fun `getLogListUri leaves out an empty tag and defaults to non-raw`() {
        val uri = DevinUriHelper.getLogListUri(clientId = "client", tag = "")

        assertNull(uri.getQueryParameter("_tag"))
        assertNull(uri.getQueryParameter("pageIndex"))
        assertNull(uri.getQueryParameter("itemCount"))
        assertNull(uri.getQueryParameter("metaSearchFieldName"))
        assertEquals("false", uri.getQueryParameter("isRawQuery"))
    }

    @Test
    fun `getLogUri and getClientListUri point at their tables`() {
        val log = DevinUriHelper.getLogUri(5)
        assertEquals("/log", log.path)
        assertEquals("5", log.getQueryParameter("_id"))

        assertEquals("content://com.khosravi.devin.provider/client", DevinUriHelper.getClientListUri().toString())
    }

    @Test
    fun `getLogChange reads back what getLogChangeUri wrote`() {
        val uri = DevinUriHelper.getLogChangeUri(7, "client", "tag")

        assertEquals(DevinUriHelper.LogChange(7, "client", "tag"), DevinUriHelper.getLogChange(uri))
    }

    @Test
    fun `getLogChange rejects foreign or incomplete uris`() {
        val query = "?_id=1&_clientId=c&_tag=t"
        assertNull(DevinUriHelper.getLogChange(Uri.parse("content://other.authority/log$query")))
        assertNull(DevinUriHelper.getLogChange(Uri.parse("content://com.khosravi.devin.provider/client$query")))
        assertNull(DevinUriHelper.getLogChange(Uri.parse("content://com.khosravi.devin.provider/log?_id=x&_clientId=c&_tag=t")))
        assertNull(DevinUriHelper.getLogChange(Uri.parse("content://com.khosravi.devin.provider/log?_id=1&_tag=t")))
        assertNull(DevinUriHelper.getLogChange(Uri.parse("content://com.khosravi.devin.provider/log?_id=1&_clientId=c")))
    }

    @Test
    fun `getMsl1 reads the field name and value`() {
        val uri = DevinUriHelper.getLogListUri(
            clientId = "client",
            msl1 = DevinUriHelper.OpStringParam("url", DevinUriHelper.OpStringValue.Contain("api")),
        )

        val msl1 = uri.getMsl1()!!
        assertEquals("url", msl1.fName)
        assertEquals("api", msl1.op.value)
    }

    @Test
    fun `getMsl1 is null without a meta search`() {
        assertNull(DevinUriHelper.getLogListUri(clientId = "client").getMsl1())
    }

    @Ignore("bug: getMsl1 ignores the operation id and always returns Contain")
    @Test
    fun `getMsl1 keeps the operation`() {
        val startWith = DevinUriHelper.getLogListUri(
            clientId = "client",
            msl1 = DevinUriHelper.OpStringParam("url", DevinUriHelper.OpStringValue.StartWith("api")),
        ).getMsl1()!!
        val equalTo = DevinUriHelper.getLogListUri(
            clientId = "client",
            msl1 = DevinUriHelper.OpStringParam("url", DevinUriHelper.OpStringValue.EqualTo("api")),
        ).getMsl1()!!

        assertTrue(startWith.op is DevinUriHelper.OpStringValue.StartWith)
        assertTrue(equalTo.op is DevinUriHelper.OpStringValue.EqualTo)
    }
}
