package com.khosravi.devin.write

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.khosravi.devin.write.room.ClientTable
import com.khosravi.devin.write.room.DevinDB
import com.khosravi.devin.write.room.LogTable
import java.util.Date

class DevinContentProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, LogTable.TABLE_NAME, CODE_LOG_ALL)
        addURI(AUTHORITY, ClientTable.TABLE_NAME, CODE_CLIENT)
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
        val context: Context = context ?: return null
        val code = matcher.match(uri)
        return when (code) {
            CODE_LOG_ALL -> {
                val clientId = selectionArgs?.get(0)
                require(!clientId.isNullOrEmpty())

                DevinDB.getInstance(context).logDao()
                    .getAllAsCursor(clientId)
            }

            CODE_CLIENT -> {
                DevinDB.getInstance(context).clientDao()
                    .getAllAsCursor()
            }

            else -> throw java.lang.IllegalArgumentException("on query Unknown URI: $uri")
        }
    }

    override fun getType(uri: Uri): String {
        return when (matcher.match(uri)) {
            CODE_LOG_ALL -> URI_ALL_LOG
            CODE_CLIENT -> URI_ROOT_CLIENT
            else -> throw java.lang.IllegalArgumentException("on getType Unknown URI: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = context ?: return null
        when (matcher.match(uri)) {
            CODE_LOG_ALL -> {
                val logTable = values?.readAsNewLogTable() ?: return null
                val id: Long = DevinDB.getInstance(context).logDao()
                    .insert(logTable)
                context.contentResolver.notifyChange(uri, null)
                ContentUris.withAppendedId(uri, id)
                return uri
            }

            CODE_CLIENT -> {
                val tableData = values?.readAsNewClientTable() ?: return null
                DevinDB.getInstance(context).clientDao()
                    .put(tableData)
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
                val clientId = uri.getQueryParameter(KEY_CLIENT_ID)!!
                val context = context ?: return -1
                DevinDB.getInstance(context).logDao()
                    .removeLogs(clientId)
                return 1
            }

            CODE_CLIENT -> {
                val context = context ?: return -1
                DevinDB.getInstance(context).clientDao()
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
            tag = getAsString(LogTable.COLUMN_TAG),
            value = getAsString(LogTable.COLUMN_VALUE),
            date = getAsLong(LogTable.COLUMN_DATE),
            meta = getAsString(LogTable.COLUMN_META),
            clientId = getAsString(LogTable.COLUMN_CLIENT_ID),
        )
    }

    private fun ContentValues.readAsNewClientTable() = ClientTable(
        packageId = getAsString(ClientTable.COLUMN_PACKAGE_ID),
    )

    companion object {

        private const val AUTHORITY = "com.khosravi.devin.provider"
        private const val SCHEME = "content"
        private const val TABLE_LOG = LogTable.TABLE_NAME
        private const val TABLE_CLIENT = ClientTable.TABLE_NAME
        private const val KEY_CLIENT_ID = "clientId"

        const val URI_ALL_LOG = "$SCHEME://$AUTHORITY/$TABLE_LOG"
        const val URI_ROOT_CLIENT = "$SCHEME://$AUTHORITY/$TABLE_CLIENT"

        const val CODE_LOG_ALL = 1
        const val CODE_CLIENT = 2

        fun contentValueLog(appId: String, tag: String, value: String, meta: String?, date: Date = Date()) =
            ContentValues().apply {
                put(LogTable.COLUMN_TAG, tag)
                put(LogTable.COLUMN_VALUE, value)
                put(LogTable.COLUMN_DATE, date.time)
                put(LogTable.COLUMN_META, meta)
                put(LogTable.COLUMN_CLIENT_ID, appId)
            }

        fun contentValuePutClient(packageId: String) = ContentValues().apply {
            put(LogTable.COLUMN_CLIENT_ID, packageId)
        }

        fun uriOfClient(): Uri = Uri.parse(URI_ROOT_CLIENT)

        fun uriOfAllLog(clientId: String = ""): Uri = Uri.parse(URI_ALL_LOG.plus("?$KEY_CLIENT_ID=$clientId"))

    }

}