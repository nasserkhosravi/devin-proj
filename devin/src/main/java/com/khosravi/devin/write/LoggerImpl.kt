package com.khosravi.devin.write

import android.util.Log
import com.khosravi.devin.write.api.DevinLogFlagsApi
import io.nasser.devin.api.DevinLogger
import org.json.JSONObject

internal class LoggerImpl(
    private val logCore: LogCore,
) : DevinLogger {

    override fun doIfEnable(action: (DevinLogger) -> Unit) {
        if (logCore.isEnable) {
            action(this)
        }
    }

    override fun debug(tag: String?, message: String, payload: String?, throwable: Throwable?) {
        sendUserLog(tag, message, Log.DEBUG, payload, throwable)
    }

    override fun info(tag: String?, message: String, payload: String?, throwable: Throwable?) {
        sendUserLog(tag, message, Log.INFO, payload, throwable)
    }

    override fun error(tag: String?, message: String, payload: String?, throwable: Throwable?) {
        sendUserLog(tag, message, Log.ERROR, payload, throwable)
    }

    override fun warning(tag: String?, message: String, payload: String?, throwable: Throwable?) {
        sendUserLog(tag, message, Log.WARN, payload, throwable)
    }

    override fun logCallerFunc() {
        if (!logCore.isEnable) {
            return
        }
        // One for logCallerFunc parent
        // And add another one if [logCallerFunc] has default parameter
        val result = TraceLogger.callerFuncInfo(1, true)
        if (result.isSuccess) {
            val analysed = result.getOrThrow()
            val parenName = analysed.parenName
            if (parenName.isNullOrEmpty()) {
                sendLog(LOG_TAG_FUNC_TRACE, analysed.methodName, null)
                return
            }
            val fParentName: String = returnSimplifiedParentName(analysed)
            sendLog(LOG_TAG_FUNC_TRACE, "$fParentName ${analysed.methodName}", analysed.toJsonString())
        } else {
            sendLog(LOG_TAG_FUNC_TRACE, result.exceptionOrNull()!!.message!!, null)
        }
    }

    private fun sendLog(tag: String?, value: String, meta: JSONObject? = null) {
        logCore.sendLog(tag, value, meta)
    }

    /**
     * @return simplified version of parent name because stacktrace classname is full package
     * For example: the function get com.example.abc.HelloWord.kt then return HelloWord.kt
     */
    private fun returnSimplifiedParentName(analysed: TraceLogger.AnalysedStacktrace): String {
        val parenName = analysed.parenName
        require(!parenName.isNullOrEmpty())
        return if (analysed.isClassName) {
            //remove package to simplify returned class name because
            parenName.substring(parenName.lastIndexOf(".") + 1)
        } else parenName
    }

    private fun sendUserLog(tag: String?, value: String, logLevel: Int, payload: String?, throwable: Throwable? = null) {
        if (logCore.isEnable.not()) return
        sendLog(tag, value, createMetaFromUserPayload(logLevel, payload, throwable))
    }

    private fun TraceLogger.AnalysedStacktrace.toJsonString() = JSONObject()
        .put(KEY_META_TYPE, LOG_TAG_FUNC_TRACE)
        .put(KEY_LOG_FUNC_TRACE_METHOD_NAME, methodName)
        .put(KEY_LOG_FUNC_TRACE_PARENT_NAME, parenName)
        .put(KEY_LOG_FUNC_TRACE_CLASS_NAME, isClassName)

    private fun createMetaFromUserPayload(logLevel: Int, payload: String?, throwable: Throwable?) = JSONObject()
        .put(KEY_META_TYPE, VALUE_LOG_META_TYPE)
        .commonMeta(logLevel, payload, throwable)

    companion object {

        const val LOG_TAG_UNTAG = "untag"
        const val KEY_META_TYPE = "meta_type"

        //log
        const val VALUE_LOG_META_TYPE = "user_log"
        const val KEY_LOG_PAYLOAD = "payload"
        const val KEY_LOG_THROWABLE = "throwable"

        //log trace
        const val LOG_TAG_FUNC_TRACE = "devin_trace"
        private const val KEY_LOG_FUNC_TRACE_METHOD_NAME = "method_name"
        private const val KEY_LOG_FUNC_TRACE_PARENT_NAME = "parent_name"
        private const val KEY_LOG_FUNC_TRACE_CLASS_NAME = "is_class_name"

        /**
         * the logs that their source are devin components such as devin_image
         */
        fun createMetaForComponentLogs(metaTypeId: String, logLevel: Int, payload: String?, throwable: Throwable?) = JSONObject()
            .put(KEY_META_TYPE, metaTypeId)
            .commonMeta(logLevel, payload, throwable)

        private fun JSONObject.commonMeta(logLevel: Int, payload: String?, throwable: Throwable?): JSONObject {
            return put(DevinLogFlagsApi.KEY_LOG_LEVEL, logLevel)
                .apply {
                    if (payload != null) {
                        put(KEY_LOG_PAYLOAD, payload)
                    }
                    if (throwable != null) {
                        put(KEY_LOG_THROWABLE, Log.getStackTraceString(throwable))
                    }
                }
        }
    }
}