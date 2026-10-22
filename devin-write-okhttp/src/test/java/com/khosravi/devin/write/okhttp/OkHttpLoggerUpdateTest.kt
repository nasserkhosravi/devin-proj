package com.khosravi.devin.write.okhttp

import android.net.Uri
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.khosravi.devin.write.api.DevinLogCore
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * What [OkHttpLoggerImpl] stores when a call it inserted gets its response or fails. Robolectric gives a real
 * [Uri], which the logger needs to update the stored call.
 */
@RunWith(RobolectricTestRunner::class)
class OkHttpLoggerUpdateTest {

    private class FakeLogCore : DevinLogCore {
        val uri: Uri = Uri.parse("content://com.khosravi.devin.provider/log/1")
        val updated = ArrayList<String?>()

        override fun insertLog(
            tag: String?, value: String, typeId: String?, meta: String?, content: ByteArray?, metaIndex: Pair<String, String>?
        ): Uri = uri

        override fun updateLog(itemId: Uri, tag: String?, value: String, typeId: String, meta: String?, content: ByteArray?): Int {
            assertEquals(uri, itemId)
            updated.add(meta)
            return DevinLogCore.FLAG_OPERATION_SUCCESS
        }

        override fun isEnable(): Boolean = true
    }

    private val core = FakeLogCore()
    private val logger = OkHttpLoggerImpl(core)

    /** A client whose "server" is a final interceptor, so nothing hits the network. */
    private fun client(server: (Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor(logger.getOrCreateInterceptor()!!)
        .addInterceptor { chain -> server(chain.request()) }
        .build()

    private fun storedUpdate(): JsonObject = JsonParser.parseString(core.updated.single()).asJsonObject

    @Test
    fun `a failed call stores its error with sensitive values masked`() {
        val request = Request.Builder().url("https://api.x.io/login?token=s3cret").build()
        val failing = client { throw IOException("unexpected end of stream on https://api.x.io/login?token=s3cret for a@b.co") }

        try {
            failing.newCall(request).execute()
            fail("the app should still get the failure")
        } catch (e: IOException) {
            // Expected: the interceptor rethrows the original exception.
        }

        val meta = storedUpdate()
        assertEquals(DevinHttpFlagsApi.Status.NETWORK_ERROR, meta.get(DevinHttpFlagsApi.KEY_STATUS_TYPE).asInt)
        val error = meta.get(DevinHttpFlagsApi.KEY_SUMMERY_OF_ERROR).asString
        assertTrue(error, error.contains("unexpected end of stream"))
        assertFalse(error, error.contains("s3cret"))
        assertFalse(error, error.contains("a@b.co"))
        assertFalse(meta.toString(), meta.toString().contains("s3cret"))
    }

    @Test
    fun `a completed call stores its response with sensitive values masked`() {
        val request = Request.Builder().url("https://api.x.io/me?token=s3cret").build()
        val ok = client {
            Response.Builder()
                .request(it).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Set-Cookie", "session=abc123")
                .body("""{"name":"ali","phone":"09121234567"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }

        ok.newCall(request).execute().use { assertEquals("""{"name":"ali","phone":"09121234567"}""", it.body!!.string()) }

        val meta = storedUpdate()
        assertEquals(DevinHttpFlagsApi.Status.HTTP_CODE, meta.get(DevinHttpFlagsApi.KEY_STATUS_TYPE).asInt)
        val stored = meta.toString()
        assertFalse(stored, stored.contains("s3cret"))
        assertFalse(stored, stored.contains("abc123"))
        assertFalse(stored, stored.contains("09121234567"))
        assertTrue(stored, stored.contains("ali"))
    }
}
