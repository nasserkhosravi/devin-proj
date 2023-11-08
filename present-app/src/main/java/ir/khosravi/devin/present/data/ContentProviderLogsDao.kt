package ir.khosravi.devin.present.data

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import ir.khosravi.devin.write.DevinContentProvider
import ir.khosravi.devin.write.room.LogTable

object ContentProviderLogsDao {

    fun getAll(context: Context): List<LogTable> {
        val cursor =
            context.contentResolver.query(DevinContentProvider.URI_ALL_LOG.toUri(), null, null, null, null) ?: return emptyList()
        val result = ArrayList<LogTable>()
        while (cursor.moveToNext()) {
            val element = DevinContentProvider.readAsNewLogTable(cursor)
            result.add(element)
        }
        return result
    }

    fun clear(context: Context) {
        val uri = DevinContentProvider.URI_ALL_LOG.toUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.contentResolver.delete(uri, null)
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }
}