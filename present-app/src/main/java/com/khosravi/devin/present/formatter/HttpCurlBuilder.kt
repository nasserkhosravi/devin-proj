package com.khosravi.devin.present.formatter

import com.google.gson.JsonObject
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

            request.postData?.text?.let { textInstance ->
                val fText = when (textInstance) {
                    is JsonObject -> textInstance.toString()
                    is String -> textInstance
                    else -> textInstance.toString()
                }

                if (fText.isNotEmpty()) {
                    writeUtf8(" --data-raw '${fText}'")
                }
            }
        }
    }

    fun toBuffer(data: HarRequest): Source {
        return buildCurl(data)
    }
}