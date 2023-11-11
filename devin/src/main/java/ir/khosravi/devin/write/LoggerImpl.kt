package ir.khosravi.devin.write

import android.content.Context
import androidx.core.net.toUri

internal class LoggerImpl(
    private val appContext: Context
) : DevinLogger {

    override fun log(message: String) {
        sendLog(LOG_TYPE_UNTAG, message)
    }

    override fun log(tag: String, message: String) {
        sendLog(tag, message)
    }

    override fun logCallerFunc() {
        sendLog(LOG_TYPE_UNTAG, TraceLogger.callerFuncInfo(1, false))
    }

    override fun logCallerFunc(tag: String?, message: String?, enableParentName: Boolean) {
        // 2, one for logCallerFunc parent, one for logCallerFunc has default parameter
        val value = TraceLogger.callerFuncInfo(2, enableParentName).let {
            if (!message.isNullOrEmpty()) it.plus(" $message")
            else it
        }
        sendLog(tag, value)
    }

    private fun sendLog(type: String?, value: String) {
        val fType = if (type.isNullOrEmpty()) LOG_TYPE_UNTAG else type
        appContext.contentResolver.insert(
            DevinContentProvider.URI_ALL_LOG.toUri(),
            DevinContentProvider.contentValueLog(fType, value)
        )
    }

    companion object {

        const val LOG_TYPE_UNTAG = "untag"
    }
}