package com.khosravi.devin.present.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.core.database.getStringOrNull
import com.khosravi.devin.write.DevinContentProvider
import com.khosravi.devin.write.api.DevinImageFlagsApi
import org.json.JSONObject

object ContentProviderLogsDao {

    private fun getAllLogUri(clientId: String): Uri {
        return DevinContentProvider.uriOfAllLog(clientId)
    }

    fun getAll(context: Context, clientId: String): List<LogData> {
        val cursor =
            context.contentResolver.query(getAllLogUri(clientId), null, null, arrayOf(clientId), null) ?: return emptyList()
        val result = ArrayList<LogData>()
        while (cursor.moveToNext()) {
            val element = cursor.asLogModel()
            result.add(element)
        }
        return result
    }

    fun clear(context: Context, clientId: String) {
        val uri = getAllLogUri(clientId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.contentResolver.delete(uri, null)
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun Cursor.asLogModel(): LogData {
        val cursor = this
        return LogData(
            id = cursor.getLong(0),
            tag = cursor.getString(1),
            value = cursor.getString(2),
            date = cursor.getLong(3),
            meta = cursor.getStringOrNull(4),
            packageId = cursor.getString(5),
        )
    }

    fun getLogImages(context: Context, clientId: String): List<ImageLogData> {
        //TODO: do direct operation to contentResolver.query.
        return getAll(context, clientId).filter { it.tag == DevinImageFlagsApi.LOG_TAG }.map {
            val imageMetaJson = JSONObject(it.meta!!)
            val url = imageMetaJson.getString(DevinImageFlagsApi.KEY_IMAGE_URL)
            val status = imageMetaJson.getInt(DevinImageFlagsApi.KEY_IMAGE_STATUS)
            ImageLogData(it.value, url = url, status = status, it.date)
        }
    }
}