package com.khosravi.devin.write

import android.util.Log
import com.khosravi.devin.write.api.DevinImageFlagsApi
import io.nasser.devin.api.DevinImageLogger
import java.net.URI

internal class DevinImageLoggerImpl(
    private val logger: LogCore
) : DevinImageLogger {

    override fun downloading(url: String, name: String?, payload: String?) {
        logImage(url, name, DevinImageFlagsApi.Status.DOWNLOADING, Log.INFO, payload, null)
    }

    override fun failed(url: String, name: String?, payload: String?, throwable: Throwable?) {
        logImage(url, name, DevinImageFlagsApi.Status.FAILED, Log.ERROR, payload, throwable)
    }

    override fun succeed(url: String, name: String?, payload: String?) {
        logImage(url, name, DevinImageFlagsApi.Status.SUCCEED, Log.INFO, payload, null)
    }

    private fun logImage(
        url: String,
        name: String?,
        status: Int,
        logLevel: Int,
        payload: String?,
        throwable: Throwable?
    ) {
        if (logger.isEnable.not()) return
        val fName = name.takeIf { !it.isNullOrEmpty() } ?: URI(url).path
        val meta = LoggerImpl.createMetaForComponentLogs(
            DevinImageFlagsApi.VALUE_IMAGE_META_TYPE, logLevel, payload, throwable
        ).put(DevinImageFlagsApi.KEY_IMAGE_URL, url)
            .put(DevinImageFlagsApi.KEY_IMAGE_STATUS, status)

        logger.sendLog(DevinImageFlagsApi.LOG_TAG, fName, meta)
    }

}