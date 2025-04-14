package com.khosravi.devin.write.okhttp.support

import okhttp3.Request
import okhttp3.Response
import okio.ByteString
import okio.IOException

/**
 * Decodes HTTP request and response bodies to human–readable texts.
 */
interface DevinOkHttpBodyDecoder {

    /**
     * Return null if request decoding is not available otherwise return decoded response as [String]
     */
    @Throws(IOException::class)
    fun decodeRequest(
        request: Request,
        body: ByteString,
    ): String?

    /**
     * Return null if response decoding is not available otherwise return decoded response as [String]
     */
    @Throws(IOException::class)
    fun decodeResponse(
        response: Response,
        body: ByteString,
    ): String?
}
