package com.khosravi.devin.write

import android.content.Context
import android.net.Uri
import android.util.Log
import io.nasser.devin.api.DevinLogger
import org.json.JSONObject

internal class LoggerImpl(
    private val appContext: Context,
    private val isEnable: Boolean
) : DevinLogger {

    private val appId = appContext.packageName

    override fun doIfEnable(action: (DevinLogger) -> Unit) {
        if (isEnable) {
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
        if (!isEnable) {
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

    /**
     * @return simplified version of parent name because stacktrace classname is full package
     * For example: the function get com.example.abc.HelloWord.kt then return HelloWord.kt
     */
    private fun returnSimplifiedParentName(analysed: TraceLogger.AnalysedStacktrace): String {
        val parenName = analysed.parenName
        require(!parenName.isNullOrEmpty())
        return if (analysed.isClassName) {
            //remove package to simplify returned class name because
            parenName.substring(parenName.lastIndexOf(".")+1)
        } else parenName
    }

    private fun sendUserLog(tag: String?, value: String, logLevel: Int, payload: String?, throwable: Throwable? = null) {
        if (isEnable.not()) return
        sendLog(tag, value, createMetaFromUserPayload(logLevel, payload, throwable))
    }

    private fun sendLog(tag: String?, value: String, meta: JSONObject? = null) {
        if (isEnable.not()) return

        val fTag = if (tag.isNullOrEmpty()) LOG_TAG_UNTAG else tag
        appContext.contentResolver.insert(
            Uri.parse(DevinContentProvider.URI_ALL_LOG),
            DevinContentProvider.contentValueLog(appId, fTag, value, meta?.toString())
        )
    }

    private fun TraceLogger.AnalysedStacktrace.toJsonString() = JSONObject()
        .put(KEY_META_TYPE, LOG_TAG_FUNC_TRACE)
        .put("method_name", methodName)
        .put("parent_name", parenName)
        .put("is_class_name", isClassName)

    private fun createMetaFromUserPayload(logLevel: Int, payload: String?, throwable: Throwable?) = JSONObject()
        .put(KEY_META_TYPE, VALUE_USER_PAYLOAD)
        .put(KEY_LOG_LEVEL, logLevel)
        .apply {
            if (payload != null) {
                put(KEY_LOG_PAYLOAD, payload)
            }
            if (throwable != null) {
                put(KEY_LOG_THROWABLE, Log.getStackTraceString(throwable))
            }
        }


    companion object {

        const val LOG_TAG_UNTAG = "untag"
        const val LOG_TAG_FUNC_TRACE = "devin trace"

        const val VALUE_USER_PAYLOAD = "_user_payload"
        const val KEY_META_TYPE = "_meta_type"
        const val KEY_LOG_LEVEL = "_log_level"
        const val KEY_LOG_PAYLOAD = "_payload"
        const val KEY_LOG_THROWABLE = "_throwable"
    }
}