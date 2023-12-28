package com.khosravi.devin.present.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build

object ContentProviderLogsDao {

    const val PERMISSION_READ = "com.khosravi.devin.permission.READ"
    const val PERMISSION_WRITE = "com.khosravi.devin.permission.WRITE"

    private fun getAllLogUri(): Uri {
        //TODO: dynamic it to support multi app
        return Uri.parse("content://com.khosravi.devin.provider/log")
    }

    fun getAll(context: Context): List<LogTable> {
        val cursor =
            context.contentResolver.query(getAllLogUri(), null, null, null, null) ?: return emptyList()
        val result = ArrayList<LogTable>()
        while (cursor.moveToNext()) {
            val element = cursor.asLogModel()
            result.add(element)
        }
        return result
    }

    fun clear(context: Context) {
        val uri = getAllLogUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.contentResolver.delete(uri, null)
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun Cursor.asLogModel(): LogTable {
        val cursor = this
        return LogTable(
            id = cursor.getLong(0),
            tag = cursor.getString(1),
            value = cursor.getString(2),
            date = cursor.getLong(3)
        )
    }
}