package com.khosravi.devin.write

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.khosravi.devin.read.DevinPersistenceFlagsApi
import com.khosravi.devin.write.api.DevinLogCore
import com.khosravi.devin.write.room.ClientTable
import com.khosravi.devin.write.room.DevinDB
import com.khosravi.devin.write.room.LogTable
import java.io.File
import java.util.Date

class DevinContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context: Context = context ?: return null
        if (!uri.isDevinScope()) return null

        if (uri.isLogPath()) {

            uri.getLogId()?.let {
                try {
                    val id = it.toLong()
                    return DevinDB.getInstance(context).logDao().getLog(id)
                } catch (e: Exception) {
                    return null
                }
            }

            val clientId = uri.getClientId() ?: return null
            val tag = uri.getQueryParameter(KEY_LOG_TAG)
            if (!tag.isNullOrEmpty()) {
                return getLogListByTagAsCursor(context, clientId, tag)
            }
            return getLogListCursor(context, clientId)
        } else if (uri.isClientPath()) {
            return getClientListCursor(context)
        }
        Log.d(TAG, "unknown query() operation with uri: $uri")
        return null
    }

    private fun getLogListByTagAsCursor(context: Context, clientId: String, tag: String) =
        DevinDB.getInstance(context).logDao().getAllLogsByTagAsCursor(clientId, tag)

    private fun getLogListCursor(context: Context, clientId: String) = DevinDB.getInstance(context).logDao()
        .getAllAsCursor(clientId)

    private fun getClientListCursor(context: Context) = DevinDB.getInstance(context).clientDao().getAllAsCursor()

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = context ?: return null
        if (!uri.isDevinScope()) return null

        if (uri.isLogPath()) {
            val logTable = values?.readAsNewLogTable() ?: return null
            val id: Long = DevinDB.getInstance(context).logDao()
                .insert(logTable)
            writeContentFileIfItsPossible(values, context, logTable.clientId, logTable.id)
            context.contentResolver.notifyChange(uri, null)
            return ContentUris.withAppendedId(uri, id)
        }

        if (uri.isClientPath()) {
            val tableData = values?.readAsNewClientTable() ?: return null
            val id = DevinDB.getInstance(context).clientDao()
                .put(tableData)
            return ContentUris.withAppendedId(uri, id)
        }

        Log.d(TAG, "unknown insert() operation with uri: $uri, values:$values")
        return null
    }

    private fun writeContentFileIfItsPossible(
        values: ContentValues,
        context: Context,
        clientId: String,
        logId: Long,
    ) {
        val rawData = values.getAsByteArray(DevinPersistenceFlagsApi.KEY_CONTENT)
        if (rawData != null) {
            val clientDir = File(context.filesDir, "$clientId/$logId")
            if (!clientDir.exists()) {
                if (!clientDir.mkdir()) {
                    Log.e(TAG, "Error in creating content dir")
                }
            }
            val contentFile = File(clientDir, "file_content")
            contentFile.writeBytesSafe(rawData)
            ///data/user/0/com.khosravi.devin.present/files/com.khosravi.sample.devin/123/content
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return delete(uri, null)
    }

    override fun delete(uri: Uri, extras: Bundle?): Int {
        val context = context ?: return FLAG_OPERATION_FAILED
        if (!uri.isDevinScope()) return FLAG_OPERATION_FAILED
        if (uri.isLogPath()) {
            val clientId = uri.getQueryParameter(KEY_CLIENT_ID)!!
            DevinDB.getInstance(context).logDao()
                .removeLogs(clientId)
            return FLAG_OPERATION_SUCCESS
        }
        if (uri.isClientPath()) {
            DevinDB.getInstance(context).clientDao()
                .nukeTable()
            return FLAG_OPERATION_SUCCESS
        }
        Log.d(TAG, "unknown delete() operation with uri: $uri")

        return FLAG_OPERATION_FAILED
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val context = context ?: return FLAG_OPERATION_FAILED
        if (!uri.isDevinScope()) return FLAG_OPERATION_FAILED

        if (uri.isLogPath()) {
            val id = uri.lastPathSegment?.toLongOrNull() ?: return FLAG_OPERATION_FAILED
            val logTable = values?.readAsNewLogTable(id) ?: return FLAG_OPERATION_FAILED
            DevinDB.getInstance(context).logDao()
                .update(logTable)
            writeContentFileIfItsPossible(values, context, logTable.clientId, logTable.id)
            context.contentResolver.notifyChange(uri, null)
            return FLAG_OPERATION_SUCCESS
        }
        return FLAG_OPERATION_FAILED
    }


    private fun ContentValues.readAsNewLogTable(id: Long = 0): LogTable {
        return LogTable(
            id = id,
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
        private const val TAG = "DevinContentProvider"

        private const val AUTHORITY = "com.khosravi.devin.provider"
        private const val SCHEME = "content"
        private const val TABLE_LOG = LogTable.TABLE_NAME
        private const val TABLE_CLIENT = ClientTable.TABLE_NAME
        private const val KEY_CLIENT_ID = "clientId"
        private const val KEY_LOG_ID = "logId"
        private const val KEY_LOG_TAG = "tag"

        private const val URI_ALL_LOG = "$SCHEME://$AUTHORITY/$TABLE_LOG"
        private const val URI_ROOT_CLIENT = "$SCHEME://$AUTHORITY/$TABLE_CLIENT"

        private const val FLAG_OPERATION_FAILED = DevinLogCore.FLAG_OPERATION_FAILED
        private const val FLAG_OPERATION_SUCCESS = DevinLogCore.FLAG_OPERATION_SUCCESS

        private val mUriOfAllLog: Uri by lazy { Uri.parse(URI_ALL_LOG) }

        fun contentValueLog(
            appId: String,
            tag: String,
            value: String,
            meta: String?,
            content: ByteArray?,
            date: Date = Date()
        ) =
            ContentValues().apply {
                put(DevinPersistenceFlagsApi.KEY_TAG, tag)
                put(DevinPersistenceFlagsApi.KEY_VALUE, value)
                put(DevinPersistenceFlagsApi.KEY_DATE, date.time)
                put(DevinPersistenceFlagsApi.KEY_META, meta)
                put(DevinPersistenceFlagsApi.KEY_CLIENT_ID, appId)
                put(DevinPersistenceFlagsApi.KEY_CONTENT, content)
            }

        fun contentValuePutClient(packageId: String) = ContentValues().apply {
            put(LogTable.COLUMN_CLIENT_ID, packageId)
        }

        fun uriOfClient(): Uri = Uri.parse(URI_ROOT_CLIENT)

        fun uriOfAllLog(): Uri = mUriOfAllLog

        fun uriOfAllLog(clientId: String, tag: String? = null): Uri {
            val builder = Uri.parse(URI_ALL_LOG.plus("?$KEY_CLIENT_ID=$clientId")).buildUpon()
            if (!tag.isNullOrEmpty()) {
                builder.appendQueryParameter(KEY_LOG_TAG, tag)
            }
            return builder.build()
        }

        fun uriOfLog(id: Long): Uri = Uri.parse(URI_ALL_LOG.plus("?$KEY_LOG_ID=$id"))

    }


    private fun Uri.getClientId(): String? = getQueryParameter(KEY_CLIENT_ID)
    private fun Uri.isDevinScope() = authority == AUTHORITY && scheme == SCHEME
    private fun Uri.isLogPath() = pathSegments.firstOrNull() == TABLE_LOG
    private fun Uri.isClientPath() = pathSegments.firstOrNull() == TABLE_CLIENT
    private fun Uri.getLogId() = getQueryParameter(KEY_LOG_ID)

    private fun File.writeBytesSafe(byteArray: ByteArray) {
        try {
            writeBytes(byteArray)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}