package com.khosravi.devin.write.okhttp.network

import com.khosravi.devin.write.okhttp.InternalLogger
import com.khosravi.devin.write.okhttp.network.entity.HttpRequestModel
import com.khosravi.devin.write.okhttp.network.entity.HttpResponseModel
import com.khosravi.devin.write.okhttp.network.support.DevinOkHttpBodyDecoder
import com.khosravi.devin.write.okhttp.network.support.JsonConverter
import com.khosravi.devin.write.okhttp.network.support.contentType
import com.khosravi.devin.write.okhttp.network.support.exclude
import com.khosravi.devin.write.okhttp.network.support.hasBody
import com.khosravi.devin.write.okhttp.network.support.toMyHttpHeaderModelList
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.ByteString
import java.io.IOException

internal class HttpLogProcessor(
    private val headersToRedact: Set<String>,
    private val bodyDecoders: Iterable<DevinOkHttpBodyDecoder>,
) {

    internal fun processRequest(request: Request): HttpRequestModel {
        val requestDate = System.currentTimeMillis()
        val headers = request.headers.exclude(headersToRedact).toMyHttpHeaderModelList().let {
            JsonConverter.serialize(it)
        }
        val requestContentType = request.body?.contentType()?.toString()
        val requestPayloadSize = request.body?.contentLength()

        val decodedContent = decodeRequestPayload(request)
        val isRequestBodyEncoded = decodedContent == null
        val headerSize = request.headers.byteCount()
        return HttpRequestModel(
            requestHeadersSize = headerSize,
            requestHeaders = headers,
            requestContentSize = requestPayloadSize,
            requestBody = decodedContent,
            requestContentType = requestContentType,
            isRequestBodyEncoded = isRequestBodyEncoded,
            method = request.method,
            requestDate = requestDate
        )
    }


    private fun decodeRequestPayload(request: Request): String? {
        val body = request.body ?: return null
        if (body.isOneShot()) {
            InternalLogger.info("Skipping one shot request body")
            return null
        }
        if (body.isDuplex()) {
            InternalLogger.info("Skipping duplex request body")
            return null
        }

        val requestSource =
            try {
                Buffer().apply { body.writeTo(this) }
            } catch (e: IOException) {
                InternalLogger.error("Failed to read request payload", e)
                return null
            }
        return decodeRequestPayload(request, requestSource.readByteString())
    }

    private fun decodeRequestPayload(
        request: Request,
        body: ByteString,
    ) = bodyDecoders.asSequence()
        .mapNotNull { decoder ->
            try {
                InternalLogger.info("Decoding with: $decoder")
                decoder.decodeRequest(request, body)
            } catch (e: IOException) {
                InternalLogger.warn("Decoder $decoder failed to process request payload", e)
                null
            }
        }.firstOrNull()


    //region response

    fun processResponse(
        response: Response,
        requestModel: HttpRequestModel,
    ): HttpResponseModel {

        // includes headers added later in the chain
        requestModel.requestHeadersSize = response.request.headers.byteCount()
        requestModel.requestHeaders = response.request.headers.exclude(headersToRedact).toMyHttpHeaderModelList().let {
            JsonConverter.serialize(it)
        }
        requestModel.requestDate = response.sentRequestAtMillis

        val responseHeadersSize = response.headers.byteCount()
        val responseHeaders = response.headers.exclude(headersToRedact).toMyHttpHeaderModelList().let { JsonConverter.serialize(it) }
        val responseDate = response.receivedResponseAtMillis
        val protocol = response.protocol.toString()
        val responseCode = response.code
        val responseMessage = response.message
        var responseTlsVersion: String? = null
        var responseCipherSuite: String? = null
        response.handshake?.let { handshake ->
            responseTlsVersion = handshake.tlsVersion.javaName
            responseCipherSuite = handshake.cipherSuite.javaName
        }

        val responseContentType = response.contentType
        val tookMs = (response.receivedResponseAtMillis - response.sentRequestAtMillis)
        val connection = response.networkResponse?.header("Connection")
        val serverIpAddress = response.networkResponse?.request?.url?.host
        val responseBody = response.body
        if (responseBody == null || !response.hasBody()) {
            return HttpResponseModel(
                responseHeadersSize, responseHeaders, responseDate, protocol, responseCode,
                responseMessage, responseTlsVersion, responseCipherSuite, responseContentType, tookMs,
                decodedBody = null,
                responseBody?.contentLength() ?: -1,
                connection,
                serverIpAddress
            )
        }

        val responseBodyString: String? = try {
            //TODO: maybe we need to a better out of memory exception handing
            decodePayload(response)
        } catch (e: Exception) {
            InternalLogger.error("Failed to read response", e)
            null
        }

        return HttpResponseModel(
            responseHeadersSize,
            responseHeaders,
            responseDate,
            protocol,
            responseCode,
            responseMessage,
            responseTlsVersion,
            responseCipherSuite,
            responseContentType,
            tookMs,
            decodedBody = responseBodyString,
            responseBody.contentLength(),
            connection,
            serverIpAddress
        )
    }

    private fun decodePayload(response: Response): String? {
        val byteString = response.peekBody(Long.MAX_VALUE).byteString()
        return bodyDecoders.asSequence()
            .mapNotNull { decoder ->
                try {
                    decoder.decodeResponse(response, byteString)
                } catch (e: IOException) {
                    InternalLogger.warn("Decoder $decoder failed to process response payload", e)
                    null
                }
            }.firstOrNull()
    }

    //end region
}