package ir.khosravi.devin.write

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import ir.khosravi.devin.write.room.DevinDB
import ir.khosravi.devin.write.room.LogTable
import java.util.Date

class DevinContentProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, LogTable.TABLE_NAME, CODE_LOG_ALL)
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val code = matcher.match(uri)
        return if (code == CODE_LOG_ALL) {
            val context: Context = context ?: return null
            val dao = DevinDB.getInstance(context).logDao()
            val cursor: Cursor = dao.getAllAsCursor()
            cursor.setNotificationUri(context.contentResolver, uri)
            cursor
        } else throw java.lang.IllegalArgumentException("on query Unknown URI: $uri")
    }

    override fun getType(uri: Uri): String {
        return when (matcher.match(uri)) {
            CODE_LOG_ALL -> URI_ALL_LOG
            else -> throw java.lang.IllegalArgumentException("on getType Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        when (matcher.match(uri)) {
            CODE_LOG_ALL -> {
                val context = context ?: return null
                val logTable = values?.readAsNewLogTable() ?: return null
                val id: Long = DevinDB.getInstance(context).logDao()
                    .insert(logTable)
                context.contentResolver.notifyChange(uri, null)
                ContentUris.withAppendedId(uri, id)
                return uri
            }

            else -> {
                throw IllegalArgumentException("on insert Unknown URI: $uri")
            }
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return delete(uri, null)
    }

    override fun delete(uri: Uri, extras: Bundle?): Int {
        when (matcher.match(uri)) {
            CODE_LOG_ALL -> {
                val context = context ?: return -1
                DevinDB.getInstance(context).logDao()
                    .nukeTable()
                return 1
            }

            else -> {
                throw IllegalArgumentException("on remove Unknown URI: $uri")
            }
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        TODO("Not supported")
    }

    private fun ContentValues.readAsNewLogTable(): LogTable {
        return LogTable(
            id = 0,
            type = getAsString(LogTable.COLUMN_TYPE),
            value = getAsString(LogTable.COLUMN_VALUE),
            date = getAsLong(LogTable.COLUMN_DATE)
        )
    }

    companion object {

        private const val AUTHORITY = "ir.khosravi.devin.provider"
        private const val SCHEME = "content"
        private const val TABLE_LOG = LogTable.TABLE_NAME
        const val URI_ALL_LOG = "$SCHEME://$AUTHORITY/$TABLE_LOG"

        const val CODE_LOG_ALL = 1

        fun contentValueLog(type: String, value: String, date: Date = Date()) = ContentValues().apply {
            put(LogTable.COLUMN_TYPE, type)
            put(LogTable.COLUMN_VALUE, value)
            put(LogTable.COLUMN_DATE, date.time)
        }

        fun readAsNewLogTable(cursor: Cursor): LogTable {
            return LogTable(
                id = cursor.getLong(0),
                type = cursor.getString(1),
                value = cursor.getString(2),
                date = cursor.getLong(3)
            )
        }


    }

}