package com.khosravi.devin.write.okhttp.network

import com.khosravi.devin.write.okhttp.InternalLogger
import com.khosravi.devin.write.okhttp.OkHttpLoggerImpl
import com.khosravi.devin.write.okhttp.network.entity.HttpTransactionStateModel
import com.khosravi.devin.write.okhttp.network.support.HarMapper
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal class NetworkInterceptor(private val logger: OkHttpLoggerImpl) : Interceptor {

    private val logProcessor = HttpLogProcessor(logger.redactHeaderName, logger.bodyDecoders)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!logger.shouldSkipRequest(request)) {
            val url = request.url.toUrl()

            val processedRequestModel = logProcessor.processRequest(request)
            val harRequestState = HarMapper.from(url, processedRequestModel, null)
            val dbLogUri = logger.onRequestSend(HttpTransactionStateModel.Requested(url, processedRequestModel), harRequestState)

            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                dbLogUri?.let {
                    logger.onResponseFailed(
                        HttpTransactionStateModel.Failed(url, processedRequestModel, dbLogUri, e), harRequestState
                    )
                }
                throw e
            }

            try {
                if (dbLogUri != null) {
                    val processedResponseModel = logProcessor.processResponse(response, processedRequestModel)
                    val harResponseState = HarMapper.from(url, processedRequestModel, processedResponseModel)
                    logger.onResponseReceived(
                        HttpTransactionStateModel.Completed(url, processedRequestModel, processedResponseModel, dbLogUri), harResponseState
                    )
                }
            } catch (e: Exception) {
                InternalLogger.error("process response failed", e)
            }
            return response
        } else {
            return chain.proceed(request)
        }
    }

}