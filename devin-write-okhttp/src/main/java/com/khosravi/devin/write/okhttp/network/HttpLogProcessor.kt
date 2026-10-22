package com.khosravi.devin.write.okhttp.network

import com.khosravi.devin.write.okhttp.InternalLogger
import com.khosravi.devin.write.okhttp.network.entity.HttpRequestModel
import com.khosravi.devin.write.okhttp.network.entity.HttpResponseModel
import com.khosravi.devin.write.okhttp.network.support.DevinOkHttpBodyDecoder
import com.khosravi.devin.write.okhttp.network.support.JsonConverter
import com.khosravi.devin.write.okhttp.network.support.Redactor
import com.khosravi.devin.write.okhttp.network.support.contentType
import com.khosravi.devin.write.okhttp.network.support.hasBody
import com.khosravi.devin.write.okhttp.network.support.toMyHttpHeaderModelList
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.ByteString
import java.io.IOException

internal class HttpLogProcessor(
    private val redactor: Redactor,
    private val bodyDecoders: Iterable<DevinOkHttpBodyDecoder>,
    /** Bodies larger than this many bytes are cut to this size. 0 or less: no limit. */
    private val maxBodyBytes: () -> Long,
) {

    /** A body as stored: its text (or a note for binary bodies), and whether it was cut. */
    private class CapturedBody(val text: String?, val isTruncated: Boolean, val note: String?)

    internal fun processRequest(request: Request): HttpRequestModel {
        val requestDate = System.currentTimeMillis()
        val headers = redactor.redactHeaders(request.headers).toMyHttpHeaderModelList().let {
            JsonConverter.serialize(it)
        }
        val requestContentType = request.body?.contentType()?.toString()
        val requestPayloadSize = request.body?.contentLength()

        val captured = captureRequestBody(request)
        val isRequestBodyEncoded = captured.text == null
        val headerSize = request.headers.byteCount()
        return HttpRequestModel(
            requestHeadersSize = headerSize,
            requestHeaders = headers,
            requestContentSize = requestPayloadSize,
            requestBody = captured.text,
            requestContentType = requestContentType,
            isRequestBodyEncoded = isRequestBodyEncoded,
            method = request.method,
            requestDate = requestDate,
            isRequestBodyTruncated = captured.isTruncated,
            requestBodyNote = captured.note,
        )
    }


    private fun captureRequestBody(request: Request): CapturedBody {
        val body = request.body ?: return NO_BODY
        if (body.isOneShot()) {
            InternalLogger.info("Skipping one shot request body")
            return NO_BODY
        }
        if (body.isDuplex()) {
            InternalLogger.info("Skipping duplex request body")
            return NO_BODY
        }

        val requestSource =
            try {
                Buffer().apply { body.writeTo(this) }
            } catch (e: IOException) {
                InternalLogger.error("Failed to read request payload", e)
                return NO_BODY
            }
        // The request is already in memory; only the part kept is decoded and stored.
        val size = requestSource.size
        val limit = maxBodyBytes()
        val kept = if (limit in 1 until size) limit else size
        val text = decodeRequestPayload(request, requestSource.readByteString(kept))
        val contentType = body.contentType()?.toString()
        return when {
            text == null && size > 0 -> binaryBody(size, contentType)
            kept < size -> CapturedBody(text, isTruncated = true, cutNote(kept, size))
            else -> CapturedBody(text, isTruncated = false, note = null)
        }
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
        requestModel.requestHeaders = redactor.redactHeaders(response.request.headers).toMyHttpHeaderModelList().let {
            JsonConverter.serialize(it)
        }
        requestModel.requestDate = response.sentRequestAtMillis

        val responseHeadersSize = response.headers.byteCount()
        val responseHeaders = redactor.redactHeaders(response.headers).toMyHttpHeaderModelList().let { JsonConverter.serialize(it) }
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

        val captured: CapturedBody = try {
            captureResponseBody(response)
        } catch (e: Exception) {
            InternalLogger.error("Failed to read response", e)
            NO_BODY
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
            decodedBody = captured.text,
            responseBody.contentLength(),
            connection,
            serverIpAddress,
            isBodyTruncated = captured.isTruncated,
            bodyNote = captured.note,
        )
    }

    /**
     * Reads the response body without consuming it. A plain (not compressed) body is only read up to the limit,
     * which saves memory and CPU on large responses. A compressed body (gzip, br) is read whole, because a cut
     * compressed body can't be decompressed by a body decoder; its decoded text is cut instead.
     */
    private fun captureResponseBody(response: Response): CapturedBody {
        val limit = maxBodyBytes()
        val contentType = response.body?.contentType()?.toString()
        val declaredSize = response.body?.contentLength()?.takeIf { it >= 0 }
        val isCompressed = response.header("Content-Encoding")?.let { !it.equals("identity", ignoreCase = true) } ?: false

        if (!isCompressed && limit > 0) {
            val peeked = response.peekBody(limit + 1).byteString()
            val isCut = peeked.size > limit
            val text = decodePayload(response, if (isCut) peeked.substring(0, limit.toInt()) else peeked)
            return when {
                text == null && peeked.size > 0 -> binaryBody(declaredSize ?: peeked.size.toLong(), contentType, sizeAtLeast = isCut && declaredSize == null)
                isCut -> CapturedBody(text, isTruncated = true, cutNote(limit, declaredSize))
                else -> CapturedBody(text, isTruncated = false, note = null)
            }
        }

        val whole = response.peekBody(Long.MAX_VALUE).byteString()
        val text = decodePayload(response, whole)
        return when {
            text == null && whole.size > 0 -> binaryBody(declaredSize ?: whole.size.toLong(), contentType)
            text != null && limit > 0 && text.length > limit ->
                CapturedBody(text.take(limit.toInt()), isTruncated = true, "Devin kept the first $limit characters of the decoded body (${text.length} characters).")
            else -> CapturedBody(text, isTruncated = false, note = null)
        }
    }

    private fun decodePayload(response: Response, byteString: ByteString): String? {
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

    private companion object {
        val NO_BODY = CapturedBody(null, isTruncated = false, note = null)

        /** Binary bodies (images, protobuf, ...) aren't stored; a short note says what was there. */
        fun binaryBody(size: Long, contentType: String?, sizeAtLeast: Boolean = false): CapturedBody {
            val note = "[binary body, ${if (sizeAtLeast) "over " else ""}${formatSize(size)}${contentType?.let { ", $it" } ?: ""}]"
            return CapturedBody(note, isTruncated = false, note = note)
        }

        fun cutNote(kept: Long, original: Long?): String =
            "Devin kept the first ${formatSize(kept)} of " + (original?.let { formatSize(it) } ?: "a larger body") + "."

        fun formatSize(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}