package com.khosravi.devin.present.data.http

import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import com.khosravi.lib.har.HarContent
import com.khosravi.lib.har.HarResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpLogOperationStatusTest {

    private fun response(status: Int) = HarResponse(
        status = status,
        statusText = "",
        httpVersion = "HTTP/1.1",
        cookies = emptyList(),
        headers = emptyList(),
        content = HarContent(0, null, "text/plain", null, null, null),
        redirectURL = "",
        comment = null,
    )

    @Test
    fun `request code maps to Requested`() {
        assertEquals(HttpLogOperationStatus.Requested, HttpLogOperationStatus.fromCode(DevinHttpFlagsApi.Status.REQUEST, null))
    }

    @Test
    fun `http code maps to Respond with the response status`() {
        assertEquals(
            HttpLogOperationStatus.Respond(404),
            HttpLogOperationStatus.fromCode(DevinHttpFlagsApi.Status.HTTP_CODE, response(404)),
        )
    }

    @Test
    fun `network error code maps to NetworkFailed`() {
        assertEquals(
            HttpLogOperationStatus.NetworkFailed,
            HttpLogOperationStatus.fromCode(DevinHttpFlagsApi.Status.NETWORK_ERROR, null),
        )
    }

    @Test
    fun `unknown code maps to Unsupported`() {
        assertEquals(HttpLogOperationStatus.Unsupported, HttpLogOperationStatus.fromCode(Int.MIN_VALUE, null))
    }
}
