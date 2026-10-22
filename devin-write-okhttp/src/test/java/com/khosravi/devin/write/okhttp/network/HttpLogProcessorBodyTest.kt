package com.khosravi.devin.write.okhttp.network

import com.google.gson.JsonObject
import com.khosravi.devin.write.okhttp.har.HarMapper
import com.khosravi.devin.write.okhttp.network.support.DefaultTextDecoder
import com.khosravi.devin.write.okhttp.network.support.DevinOkHttpBodyDecoder
import com.khosravi.devin.write.okhttp.network.support.JsonConverter
import com.khosravi.devin.write.okhttp.network.support.Redactor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

/** Body size limits and binary bodies, through [HttpLogProcessor] and [HarMapper] with real OkHttp objects. */
class HttpLogProcessorBodyTest {

    private val redactor = Redactor()
    private val json = "application/json".toMediaType()
    private val get = Request.Builder().url("https://api.x.io/rides").build()

    /** About 7 KB of JSON. */
    private val big = (0 until 200).joinToString(",", "{\"items\":[", "]}") { """{"id":$it,"title":"item number $it"}""" }

    private fun processor(limit: Long, decoders: List<DevinOkHttpBodyDecoder> = listOf(DefaultTextDecoder)) =
        HttpLogProcessor(redactor, decoders) { limit }

    private fun response(request: Request, type: String, body: ByteArray, encoding: String? = null): Response =
        Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("Content-Type", type)
            .header("Content-Length", body.size.toString())
            .apply { if (encoding != null) header("Content-Encoding", encoding) }
            .body(body.toResponseBody(type.toMediaType()))
            .sentRequestAtMillis(1).receivedResponseAtMillis(2)
            .build()

    /** The HAR entry stored for one call. */
    private fun entry(p: HttpLogProcessor, request: Request, response: Response): JsonObject {
        val requestModel = p.processRequest(request)
        val responseModel = p.processResponse(response, requestModel)
        val har = HarMapper.from(URL(request.url.toString()), requestModel, responseModel, redactor)
        return JsonConverter.toJsonTree(har)!!.asJsonObject.getAsJsonObject("log").getAsJsonArray("entries")[0].asJsonObject
    }

    private fun JsonObject.content() = getAsJsonObject("response").getAsJsonObject("content")

    @Test
    fun `a small json body is stored whole as json, redacted, without a note`() {
        val content = entry(processor(1024), get, response(get, "application/json", """{"ok":true,"email":"a@b.co"}""".toByteArray())).content()
        assertTrue(content["text"].isJsonObject)
        assertTrue(content["text"].asJsonObject["email"].asString.startsWith("[REDACTED#"))
        assertTrue(content["comment"].isJsonNull)
    }

    @Test
    fun `a big response is cut to the limit, stored as text, with a note`() {
        val content = entry(processor(1024), get, response(get, "application/json", big.toByteArray())).content()
        assertEquals(1024, content["text"].asString.length)
        assertEquals(big.take(1024), content["text"].asString)
        assertEquals("Devin kept the first 1 KB of ${big.length / 1024} KB.", content["comment"].asString)
    }

    @Test
    fun `a big request body is cut, with a note on postData`() {
        val post = Request.Builder().url("https://api.x.io/upload").post(big.toRequestBody(json)).build()
        val postData = entry(processor(1024), post, response(post, "application/json", "{}".toByteArray()))
            .getAsJsonObject("request").getAsJsonObject("postData")
        assertEquals(1024, postData["text"].asString.length)
        assertTrue(postData["comment"].asString.startsWith("Devin kept the first 1 KB"))
    }

    @Test
    fun `a binary body is replaced by a note`() {
        val png = ByteArray(5000) { (it * 31).toByte() }.also { it[0] = 0x89.toByte(); it[1] = 'P'.code.toByte() }
        val content = entry(processor(1024), get, response(get, "image/png", png)).content()
        assertEquals("[binary body, 4 KB, image/png]", content["text"].asString)
    }

    @Test
    fun `a gzip body the decoders can't read becomes a binary note, not a parse error`() {
        val gzipped = gzip(big)
        val content = entry(processor(1024), get, response(get, "application/json", gzipped, "gzip")).content()
        assertEquals("[binary body, ${gzipped.size} B, application/json]", content["text"].asString)
    }

    @Test
    fun `a gzip body read by a decompressing decoder has its decoded text cut`() {
        val gunzip = object : DevinOkHttpBodyDecoder {
            override fun decodeRequest(request: Request, body: ByteString): String? = null
            override fun decodeResponse(response: Response, body: ByteString): String? =
                GzipSource(Buffer().write(body)).buffer().readUtf8()
        }
        val content = entry(processor(1024, listOf(gunzip, DefaultTextDecoder)), get, response(get, "application/json", gzip(big), "gzip")).content()
        assertEquals(big.take(1024), content["text"].asString)
        assertEquals("Devin kept the first 1024 characters of the decoded body (${big.length} characters).", content["comment"].asString)
    }

    @Test
    fun `a limit of 0 stores the whole body as json`() {
        val content = entry(processor(0), get, response(get, "application/json", big.toByteArray())).content()
        assertTrue(content["text"].isJsonObject)
        assertTrue(content["comment"].isJsonNull)
    }

    @Test
    fun `the app still reads the whole response after logging`() {
        val response = response(get, "application/json", big.toByteArray())
        val p = processor(1024)
        p.processResponse(response, p.processRequest(get))
        assertEquals(big, response.body!!.string())
    }

    private fun gzip(text: String): ByteArray {
        val out = Buffer()
        GzipSink(out).buffer().use { it.writeUtf8(text) }
        return out.readByteArray()
    }
}
