package ir.khosravi.devin.write

import android.content.Context
import androidx.core.net.toUri

class LoggerImpl(
    private val appContext: Context
) : DevinLogger {


    override fun debug(value: String) {
        custom(LOG_TYPE_DEBUG, value)
    }

    override fun error(value: String) {
        custom(LOG_TYPE_ERROR, value)
    }

    override fun info(value: String) {
        custom(LOG_TYPE_INFO, value)
    }

    override fun warning(value: String) {
        custom(LOG_TYPE_WARNING, value)
    }

    override fun custom(type: String, value: String) {
        sendLog(type, value)
    }

    private fun sendLog(type: String, value: String) {
        appContext.contentResolver.insert(
            DevinContentProvider.URI_ALL_LOG.toUri(),
            DevinContentProvider.contentValueLog(type, value)
        )
    }

    companion object {

        const val LOG_TYPE_DEBUG = "debug"
        const val LOG_TYPE_ERROR = "error"
        const val LOG_TYPE_INFO = "info"
        const val LOG_TYPE_WARNING = "warning"
    }
}