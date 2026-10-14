package com.khosravi.devin.present.formatter

import com.google.gson.JsonObject
import com.khosravi.lib.har.HarHeader
import com.khosravi.lib.har.HarPostData
import com.khosravi.lib.har.HarRequest
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpCurlBuilderTest {

    private fun request(
        method: String = "get",
        headers: List<HarHeader> = emptyList(),
        postText: Any? = null,
    ) = HarRequest(
        method = method,
        url = "https://example.com/api",
        httpVersion = "HTTP/1.1",
        cookies = emptyList(),
        headers = headers,
        queryString = emptyList(),
        postData = postText?.let { HarPostData("application/json", emptyList(), it, null) },
        comment = null,
    )

    private fun curl(request: HarRequest) = HttpCurlBuilder.toBuffer(request).buffer().readUtf8()

    @Test
    fun `uppercases method and includes url`() {
        assertEquals("curl -X GET https://example.com/api", curl(request()))
    }

    @Test
    fun `adds each header`() {
        val result = curl(request(headers = listOf(HarHeader("Accept", "*/*", null), HarHeader("X-Id", "7", null))))
        assertEquals("curl -X GET https://example.com/api -H 'Accept: */*' -H 'X-Id: 7'", result)
    }

    @Test
    fun `adds string body as data-raw`() {
        assertEquals(
            "curl -X POST https://example.com/api --data-raw 'a=1'",
            curl(request(method = "post", postText = "a=1")),
        )
    }

    @Test
    fun `serializes json body`() {
        val body = JsonObject().apply { addProperty("k", "v") }
        assertEquals(
            "curl -X POST https://example.com/api --data-raw '{\"k\":\"v\"}'",
            curl(request(method = "post", postText = body)),
        )
    }

    @Test
    fun `omits empty body`() {
        assertEquals("curl -X POST https://example.com/api", curl(request(method = "post", postText = "")))
    }
}
