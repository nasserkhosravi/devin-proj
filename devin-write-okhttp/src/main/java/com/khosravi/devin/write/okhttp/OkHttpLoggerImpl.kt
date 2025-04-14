package com.khosravi.devin.write.okhttp

import android.net.Uri
import android.util.Log
import com.khosravi.devin.api.core.DevinLogCore
import com.khosravi.devin.read.DevinLogFlagsApi
import com.khosravi.devin.write.okhttp.har.HarConverter.toJson
import com.khosravi.devin.write.okhttp.har.HarFile
import com.khosravi.devin.write.okhttp.network.NetworkInterceptor
import com.khosravi.devin.write.okhttp.network.entity.HttpTransactionStateModel
import com.khosravi.devin.write.okhttp.network.support.DefaultTextDecoder
import com.khosravi.devin.write.okhttp.network.support.DevinOkHttpBodyDecoder
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import okhttp3.Interceptor
import okhttp3.Request
import org.json.JSONObject

internal class OkHttpLoggerImpl internal constructor(
    private val logCore: DevinLogCore,
) : DevinOkHttpLogger {

    private val isEnable: Boolean
        get() = logCore.isEnable()
    private var interceptor: NetworkInterceptor? = null
    private var logSkipper: ((Request) -> Boolean)? = null

    internal var redactHeaderName: HashSet<String> = HashSet()
    internal var bodyDecoders = ArrayList<DevinOkHttpBodyDecoder>().apply {
        add(DefaultTextDecoder)
    }

    override fun getOrCreateInterceptor(): Interceptor? {
        if (!isEnable) return null

        if (interceptor == null) {
            interceptor = NetworkInterceptor(this)
        }
        return interceptor!!
    }

    override fun setRequestsSkipper(action: (Request) -> Boolean) {
        if (!isEnable) return
        logSkipper = action
    }

    override fun addHeadersRedactor(headers: List<String>) {
        if (isEnable.not()) return
        redactHeaderName.addAll(headers)
    }

    override fun addBodyDecoder(decoder: DevinOkHttpBodyDecoder) {
        if (isEnable.not()) return
        bodyDecoders.add(decoder)
    }


    //region internal api
    internal fun shouldSkipRequest(request: Request): Boolean {
        return logSkipper?.invoke(request) ?: false
    }

    internal fun onRequestSend(model: HttpTransactionStateModel.Requested, har: HarFile): Uri? {
        val meta = createMetaJson(Log.DEBUG)
            .put(DevinHttpFlagsApi.KEY_STATUS_TYPE, DevinHttpFlagsApi.Status.REQUEST)
            .put(DevinHttpFlagsApi.KEY_URL, model.url.toString())
            .put(DevinHttpFlagsApi.KEY_HAR, har.toJson())

        val value = "..... ${model.request.method} ${model.url.path}"
        return logCore.sendLog(DevinHttpFlagsApi.LOG_TAG, value, meta)
    }

    internal fun onResponseReceived(model: HttpTransactionStateModel.Completed, har: HarFile) {
        val responseCode = model.response.responseCode
        val value = "$responseCode ${model.request.method} ${model.url.path}"
        val logLevelFlag = if (responseCode in 400..600) Log.ERROR else Log.INFO

        val meta = createMetaJson(logLevelFlag)
            .put(DevinHttpFlagsApi.KEY_STATUS_TYPE, DevinHttpFlagsApi.Status.HTTP_CODE)
            .put(DevinHttpFlagsApi.KEY_URL, model.url.toString())
            .put(DevinHttpFlagsApi.KEY_HAR, har.toJson())

        val resultLog = logCore.updateLog(model.dbUri, DevinHttpFlagsApi.LOG_TAG, value, meta)
        if (resultLog != DevinLogCore.FLAG_OPERATION_SUCCESS) {
            InternalLogger.debug("onResponseReceived updateLog failed:$resultLog")
        }
    }

    internal fun onResponseFailed(model: HttpTransactionStateModel.Failed, har: HarFile) {
        val meta = createMetaJson(Log.ERROR, model.exception)
            .put(DevinHttpFlagsApi.KEY_STATUS_TYPE, DevinHttpFlagsApi.Status.NETWORK_ERROR)
            .put(DevinHttpFlagsApi.KEY_SUMMERY_OF_ERROR, model.exception.toString())
            .put(DevinHttpFlagsApi.KEY_URL, model.url.toString().replace("\"", "\\\""))
            .put(DevinHttpFlagsApi.KEY_HAR, har.toJson())


        val value = "!!!!!! ${model.request.method} ${model.url.path}"
        val resultCode = logCore.updateLog(model.dbUri, DevinHttpFlagsApi.LOG_TAG, value, meta)
        if (resultCode != DevinLogCore.FLAG_OPERATION_SUCCESS) {
            InternalLogger.debug("onResponseFailed updateLog failed: $resultCode")
        }
    }

    private fun createMetaJson(logLevel: Int, throwable: Throwable? = null): JSONObject = JSONObject()
        .put(DevinLogFlagsApi.KEY_META_TYPE, DevinHttpFlagsApi.LOG_TAG)
        .put(DevinLogFlagsApi.KEY_LOG_LEVEL, logLevel)
        .apply {
            if (throwable != null) {
                put(DevinLogFlagsApi.KEY_LOG_THROWABLE, Log.getStackTraceString(throwable))
            }
        }

    //endregion

}