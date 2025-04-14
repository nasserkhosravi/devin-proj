package com.khosravi.devin.present.formatter

import com.khosravi.lib.har.HarRequest
import okio.Buffer
import okio.Source

object HttpCurlBuilder {

    private fun buildCurl(request: HarRequest): Source {
        return Buffer().apply {
            val url = request.url
            val method = request.method.uppercase()
            writeUtf8("curl -X $method $url")

            request.headers.forEach { header ->
                writeUtf8(" -H '${header.name}: ${header.value}'")
            }

            request.postData?.let { postData ->
                if (postData.text.isNotEmpty()) {
                    writeUtf8(" --data-raw '${postData.text}'")
                }
            }
        }
    }

    fun toBuffer(data: HarRequest): Source {
        return buildCurl(data)
    }
}