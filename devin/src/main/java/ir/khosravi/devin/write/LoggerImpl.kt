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
        sendLog(LOG_TYPE_UNTAG, TraceLogger.callerFuncInfo(1))
    }

    private fun sendLog(type: String, value: String) {
        appContext.contentResolver.insert(
            DevinContentProvider.URI_ALL_LOG.toUri(),
            DevinContentProvider.contentValueLog(type, value)
        )
    }

    companion object {

        const val LOG_TYPE_UNTAG = "untag"
    }
}