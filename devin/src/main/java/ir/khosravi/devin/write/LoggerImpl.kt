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

    override fun logCallerFunc(tag: String?, message: String?, enableParentName: Boolean) {
        val value = TraceLogger.callerFuncInfo(1, enableParentName).plus(" $message")
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