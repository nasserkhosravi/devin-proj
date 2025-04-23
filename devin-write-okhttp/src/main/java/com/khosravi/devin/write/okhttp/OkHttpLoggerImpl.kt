package com.khosravi.devin.write.okhttp

import android.net.Uri
import android.util.Log
import com.google.gson.JsonObject
import com.khosravi.devin.read.DevinLogFlagsApi
import com.khosravi.devin.write.api.DevinLogCore
import com.khosravi.devin.write.okhttp.har.HarFile
import com.khosravi.devin.write.okhttp.network.NetworkInterceptor
import com.khosravi.devin.write.okhttp.network.entity.HttpTransactionStateModel
import com.khosravi.devin.write.okhttp.network.support.DefaultTextDecoder
import com.khosravi.devin.write.okhttp.network.support.DevinOkHttpBodyDecoder
import com.khosravi.devin.write.okhttp.network.support.JsonConverter
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import okhttp3.Interceptor
import okhttp3.Request

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
        val meta = createMetaJson(model, Log.DEBUG, DevinHttpFlagsApi.Status.REQUEST, har)

        val value = "..... ${model.request.method} ${model.url.path}"
        val metaJsonString = meta.toString()
        return logCore.sendLog(
            DevinHttpFlagsApi.LOG_TAG, value, metaJsonString,
            content = metaJsonString.toByteArray(Charsets.UTF_8)
        )
    }


    internal fun onResponseReceived(model: HttpTransactionStateModel.Completed, har: HarFile) {
        val responseCode = model.response.responseCode
        val value = "$responseCode ${model.request.method} ${model.url.path}"
        val logLevelFlag = if (responseCode in 400..600) Log.ERROR else Log.INFO

        val meta = createMetaJson(model, logLevelFlag, DevinHttpFlagsApi.Status.HTTP_CODE, har)
        val metaJsonString = meta.toString()

        val resultLog = logCore.updateLog(
            model.dbUri,
            DevinHttpFlagsApi.LOG_TAG,
            value,
            metaJsonString
        )
        if (resultLog != DevinLogCore.FLAG_OPERATION_SUCCESS) {
            InternalLogger.debug("onResponseReceived updateLog failed:$resultLog")
        }
    }

    internal fun onResponseFailed(model: HttpTransactionStateModel.Failed, har: HarFile) {
        val meta = createMetaJson(model, Log.ERROR, DevinHttpFlagsApi.Status.NETWORK_ERROR, har).apply {
            addProperty(DevinHttpFlagsApi.KEY_SUMMERY_OF_ERROR, model.exception.toString())
        }

        val value = "!!!!!! ${model.request.method} ${model.url.path}"
        val resultCode = logCore.updateLog(model.dbUri, DevinHttpFlagsApi.LOG_TAG, value, meta.toString())
        if (resultCode != DevinLogCore.FLAG_OPERATION_SUCCESS) {
            InternalLogger.debug("onResponseFailed updateLog failed: $resultCode")
        }
    }

    private fun createMetaJson(model: HttpTransactionStateModel, logLevelFlag: Int, statusType: Int, har: HarFile): JsonObject {
        return createBaseMetaJson(logLevelFlag).apply {
            addProperty(DevinHttpFlagsApi.KEY_STATUS_TYPE, statusType)
            addProperty(DevinHttpFlagsApi.KEY_URL, model.url.toString())
            add(DevinHttpFlagsApi.KEY_HAR, JsonConverter.toJsonTree(har))
        }
    }

    private fun createBaseMetaJson(logLevel: Int, throwable: Throwable? = null) = JsonObject().apply {
        addProperty(DevinLogFlagsApi.KEY_META_TYPE, DevinHttpFlagsApi.LOG_TAG)
        addProperty(DevinLogFlagsApi.KEY_LOG_LEVEL, logLevel)
            .apply {
                if (throwable != null) {
                    addProperty(DevinLogFlagsApi.KEY_LOG_THROWABLE, Log.getStackTraceString(throwable))
                }
            }
    }

    //endregion

}