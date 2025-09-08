package com.khosravi.devin.write

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import com.khosravi.devin.read.DevinPersistenceFlagsApi
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.read.DevinUriHelper.getMsl1
import com.khosravi.devin.write.api.DevinLogCore
import com.khosravi.devin.write.room.ClientTable
import com.khosravi.devin.write.room.DevinDB
import com.khosravi.devin.write.room.LogTable
import com.khosravi.devin.write.room.MetaIndexTable
import org.json.JSONObject
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
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val context: Context = context ?: return null
        if (!uri.isDevinScope()) return null
        if (uri.isLogPath()) {
            if (uri.isRawQuery()) {
                val pageIndex = uri.getQueryParameter(DevinUriHelper.KEY_PAGE_INDEX)?.toIntOrNull()
                val itemCount = uri.getQueryParameter(DevinUriHelper.KEY_ITEM_COUNT)?.toIntOrNull()
                val query = buildSelectLogQuery(
                    projection, selection, selectionArgs, sortOrder,
                    pageIndex = pageIndex,
                    itemCount = itemCount,
                    msl1 = uri.getMsl1()
                )
                return DevinDB.getInstance(context).logDao().rawQuery(query)
            }

            uri.getLogId()?.let {
                try {
                    val id = it.toLong()
                    return DevinDB.getInstance(context).logDao().getLog(id)
                } catch (e: Exception) {
                    return null
                }
            }

            val clientId = uri.getClientId() ?: return null
            val tag = uri.getQueryParameter(LogTable.COLUMN_TAG)
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

    private fun buildSelectLogQuery(
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        pageIndex: Int?,
        itemCount: Int?,
        msl1: DevinUriHelper.OpStringParam?
    ): SimpleSQLiteQuery {
        val columns = projection?.joinToString(",") ?: "*"
        val fSelection = if (selection != null || msl1 != null) {
            val msResult = msl1?.let {
                """${LogTable.COLUMN_ID} IN (SELECT ${MetaIndexTable.COLUMN_ID} FROM ${MetaIndexTable.TABLE_NAME}
    WHERE field = '${msl1.fName}' AND ${msl1.toSqlQuery()}
    )"""
            }
            if (msResult != null) {
                "WHERE $msResult AND ${selection ?: ""}"
            } else {
                selection?.let {
                    "WHERE $it"
                }

            }
        } else ""

        val orderBy = sortOrder?.let {
            "ORDER BY $it"
        } ?: ""

        val fCountQuery = if (itemCount != null) {
            val offset = pageIndex?.let {
                "OFFSET ${pageIndex * itemCount}"
            } ?: ""

            "LIMIT $itemCount $offset"
        } else ""


        val sql = """SELECT $columns FROM ${LogTable.TABLE_NAME} $fSelection $orderBy $fCountQuery"""
        return SimpleSQLiteQuery(sql, selectionArgs)
    }


    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = context ?: return null
        if (!uri.isDevinScope()) return null

        val instance = DevinDB.getInstance(context)
        if (uri.isLogPath()) {
            val logTable = values?.readAsNewLogTable() ?: return null
            val id: Long = instance.logDao().insert(logTable)
            values.readMetaIndexPair()?.let {
                val resultId = instance.metaIndexDao().put(MetaIndexTable(id, it.first, it.second))
                Log.d(TAG, "put meta index: $resultId")
            }
            writeContentFileIfItsPossible(values, context, logTable.clientId, logTable.id)
            context.contentResolver.notifyChange(uri, null)
            return ContentUris.withAppendedId(uri, id).apply {
                Log.d(TAG, "Insert log: $uri")
            }
        }

        if (uri.isClientPath()) {
            val tableData = values?.readAsNewClientTable() ?: return null
            val id = instance.clientDao()
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
            typeId = getAsString(LogTable.COLUMN_TYPE_ID)
        )
    }

    private fun ContentValues.readAsNewClientTable() = ClientTable(
        packageId = getAsString(ClientTable.COLUMN_PACKAGE_ID),
        presenterConfig = getAsString(ClientTable.COLUMN_PRESENTER_CONFIG)
    )

    private fun ContentValues.readMetaIndexPair(): Pair<String, String>? {
        val name = getAsString(KEY_META_INDEX_NAME)
        val value = getAsString(KEY_META_INDEX_VALUE)
        if (name != null && value != null) return name to value
        return null
    }

    private fun DevinUriHelper.OpStringParam.toSqlQuery(): String {
        return when (this.op) {
            is DevinUriHelper.OpStringValue.Contain -> "${MetaIndexTable.COLUMN_FIELD_VALUE} LIKE '%${op.value}%'"
            is DevinUriHelper.OpStringValue.StartWith -> "${MetaIndexTable.COLUMN_FIELD_VALUE} = '${op.value}%'"
            is DevinUriHelper.OpStringValue.EqualTo -> "${MetaIndexTable.COLUMN_FIELD_VALUE} = ${op.value}"
        }
    }

    companion object {
        private const val TAG = "DevinContentProvider"

        private const val AUTHORITY = "com.khosravi.devin.provider"
        private const val SCHEME = "content"
        private const val TABLE_LOG = LogTable.TABLE_NAME
        private const val TABLE_CLIENT = ClientTable.TABLE_NAME
        private const val KEY_CLIENT_ID = ClientTable.COLUMN_PACKAGE_ID

        internal const val URI_ALL_LOG = "$SCHEME://$AUTHORITY/$TABLE_LOG"
        internal const val URI_ROOT_CLIENT = "$SCHEME://$AUTHORITY/$TABLE_CLIENT"

        private const val FLAG_OPERATION_FAILED = DevinLogCore.FLAG_OPERATION_FAILED
        private const val FLAG_OPERATION_SUCCESS = DevinLogCore.FLAG_OPERATION_SUCCESS

        private const val KEY_META_INDEX_NAME = "metaIndexName"
        private const val KEY_META_INDEX_VALUE = "metaIndexValue"

        fun contentValueLog(
            appId: String,
            tag: String,
            value: String,
            typeId: String?,
            meta: String?,
            content: ByteArray?,
            metaIndexPair: Pair<String, String>?,
            date: Date = Date()
        ) =
            ContentValues().apply {
                put(DevinPersistenceFlagsApi.KEY_TAG, tag)
                put(DevinPersistenceFlagsApi.KEY_VALUE, value)
                put(DevinPersistenceFlagsApi.KEY_TYPE_ID, typeId)
                put(DevinPersistenceFlagsApi.KEY_DATE, date.time)
                put(DevinPersistenceFlagsApi.KEY_META, meta)
                put(DevinPersistenceFlagsApi.KEY_CLIENT_ID, appId)
                put(DevinPersistenceFlagsApi.KEY_CONTENT, content)
                metaIndexPair?.let {
                    put(KEY_META_INDEX_NAME, it.first)
                    put(KEY_META_INDEX_VALUE, it.second)
                }
            }

        fun contentValuePutClient(packageId: String, presenterConfig: JSONObject?) = ContentValues().apply {
            put(ClientTable.COLUMN_PACKAGE_ID, packageId)
            put(ClientTable.COLUMN_PRESENTER_CONFIG, presenterConfig?.toString())
        }

        @Deprecated("", replaceWith = ReplaceWith("DevinUriHelper.getClientListUri()"))
        fun uriOfClient(): Uri = DevinUriHelper.getClientListUri()

        @Deprecated("", replaceWith = ReplaceWith("DevinUriHelper.getLogListUri()"))
        fun uriOfAllLog(): Uri = DevinUriHelper.getLogListUri()

        @Deprecated("", replaceWith = ReplaceWith("DevinUriHelper.getLogListUri(clientId, tag)"))
        fun uriOfAllLog(clientId: String, tag: String? = null): Uri {
            return DevinUriHelper.getLogListUri(clientId, tag, null)
        }

        @Deprecated("", replaceWith = ReplaceWith("DevinUriHelper.getLogUri(id)"))
        fun uriOfLog(id: Long): Uri = DevinUriHelper.getLogUri(id)

    }


    private fun Uri.isDevinScope() = authority == AUTHORITY && scheme == SCHEME
    private fun Uri.isLogPath() = pathSegments.firstOrNull() == TABLE_LOG
    private fun Uri.isClientPath() = pathSegments.firstOrNull() == TABLE_CLIENT
    private fun Uri.getLogId() = getQueryParameter(LogTable.COLUMN_ID)
    private fun Uri.getClientId(): String? = getQueryParameter(KEY_CLIENT_ID)
    private fun Uri.isRawQuery(): Boolean = getBooleanQueryParameter(DevinUriHelper.KEY_IS_RAW_QUERY, false)

    private fun File.writeBytesSafe(byteArray: ByteArray) {
        try {
            writeBytes(byteArray)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

