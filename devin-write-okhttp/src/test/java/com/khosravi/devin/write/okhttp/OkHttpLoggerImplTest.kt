package com.khosravi.devin.write.okhttp

import android.net.Uri
import com.google.gson.JsonParser
import com.khosravi.devin.write.api.DevinLogCore
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What [OkHttpLoggerImpl] stores for a request, through its real [okhttp3.Interceptor] in an [OkHttpClient]. */
class OkHttpLoggerImplTest {

    private class FakeLogCore(private val enabled: Boolean = true) : DevinLogCore {
        val inserted = ArrayList<String?>()

        override fun insertLog(
            tag: String?, value: String, typeId: String?, meta: String?, content: ByteArray?, metaIndex: Pair<String, String>?
        ): Uri? {
            inserted.add(meta)
            return null
        }

        override fun updateLog(itemId: Uri, tag: String?, value: String, typeId: String, meta: String?, content: ByteArray?): Int =
            DevinLogCore.FLAG_OPERATION_SUCCESS

        override fun isEnable(): Boolean = enabled
    }

    /** Sends [request] through the logger's interceptor; the server is a final interceptor, so nothing hits the network. */
    private fun send(logger: OkHttpLoggerImpl, request: Request) {
        val client = OkHttpClient.Builder()
            .addInterceptor(logger.getOrCreateInterceptor()!!)
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        client.newCall(request).execute().close()
    }

    private fun storedRequest(core: FakeLogCore) =
        JsonParser.parseString(core.inserted.single()).asJsonObject

    private fun harRequest(core: FakeLogCore) = storedRequest(core).getAsJsonObject(DevinHttpFlagsApi.KEY_HAR)
        .getAsJsonObject("log").getAsJsonArray("entries")[0].asJsonObject.getAsJsonObject("request")

    @Test
    fun `sensitive query parameters are masked in the stored url and HAR`() {
        val core = FakeLogCore()
        send(OkHttpLoggerImpl(core), Request.Builder().url("https://api.x.io/login?token=s3cret&page=2").build())

        val url = storedRequest(core).get(DevinHttpFlagsApi.KEY_URL).asString
        assertFalse(url, url.contains("s3cret"))
        assertTrue(url, url.contains("page=2"))

        val har = harRequest(core)
        assertFalse(har.toString(), har.toString().contains("s3cret"))
    }

    @Test
    fun `a json request body is stored with sensitive fields masked`() {
        val core = FakeLogCore()
        val body = """{"user":"ali","password":"hunter2"}""".toRequestBody("application/json".toMediaType())
        send(OkHttpLoggerImpl(core), Request.Builder().url("https://api.x.io/login").post(body).build())

        val text = harRequest(core).getAsJsonObject("postData").getAsJsonObject("text")
        assertEquals("ali", text.get("user").asString)
        assertTrue(text.get("password").asString.startsWith("[REDACTED#"))
    }

    @Test
    fun `fields added by the app are masked too`() {
        val core = FakeLogCore()
        val logger = OkHttpLoggerImpl(core).apply { addFieldsRedactor(listOf("nationalCode")) }
        val body = """{"national_code":"0012345678","city":"Tehran"}""".toRequestBody("application/json".toMediaType())
        send(logger, Request.Builder().url("https://api.x.io/profile").post(body).build())

        val text = harRequest(core).getAsJsonObject("postData").getAsJsonObject("text")
        assertTrue(text.get("national_code").asString.startsWith("[REDACTED#"))
        assertEquals("Tehran", text.get("city").asString)
    }

    @Test
    fun `with defaults off a sensitive query parameter is stored as is`() {
        val core = FakeLogCore()
        val logger = OkHttpLoggerImpl(core).apply { setDefaultRedactionEnabled(false) }
        send(logger, Request.Builder().url("https://api.x.io/login?token=s3cret").build())

        assertTrue(storedRequest(core).get(DevinHttpFlagsApi.KEY_URL).asString.contains("token=s3cret"))
    }

    @Test
    fun `a disabled logger gives no interceptor and ignores configuration`() {
        val logger = OkHttpLoggerImpl(FakeLogCore(enabled = false))
        logger.setMaxBodySize(10)
        logger.setDefaultRedactionEnabled(false)

        assertNull(logger.getOrCreateInterceptor())
        assertEquals(32L * 1024, logger.maxBodyBytes)
        assertTrue(logger.redactor.defaultsEnabled)
    }
}
