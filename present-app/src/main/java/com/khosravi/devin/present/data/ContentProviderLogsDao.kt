package com.khosravi.devin.present.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.core.database.getStringOrNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.khosravi.devin.present.data.http.HttpLogData
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.data.http.HttpLogOperationStatus
import com.khosravi.devin.present.data.http.UrlQuery
import com.khosravi.devin.present.data.model.GetLogsQueryModel
import com.khosravi.devin.present.data.model.PageInfo
import com.khosravi.devin.present.getInt
import com.khosravi.devin.present.getString
import com.khosravi.devin.present.optString
import com.khosravi.devin.present.present.http.GsonConverter
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.read.DevinImageFlagsApi
import com.khosravi.devin.read.DevinUriHelper.OpStringValue
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import com.khosravi.devin.write.room.LogTable
import com.khosravi.lib.har.HarEntry
import com.khosravi.lib.har.HarFile

object ContentProviderLogsDao {

    private const val TAG = "PresenterLogQuery"

    fun queryLogList(
        context: Context,
        clientId: String,
        model: GetLogsQueryModel?
    ): List<LogData> {
        return if (model == null) {
            queryLogList(context, clientId)
        } else {
            queryLogList(
                context,
                clientId,
                typeId = model.typeId,
                tag = model.tag,
                value = model.value,
                metaSearch = model.metaParam,
                page = model.page
            )
        }

    }

    fun queryLogListAsCursor(context: Context, clientId: String, model: GetLogsQueryModel?): Cursor? {
        if (model == null) {
            return getAllLogsAsCursor(context, clientId)
        }
        return queryLogListAsCursor(
            context,
            clientId,
            model.typeId,
            model.tag,
            model.value,
            model.metaParam,
            model.timeLessThan,
            model.page
        )
    }

    private fun getAllLogsAsCursor(context: Context, clientId: String): Cursor? {
        val args = arrayListOf(clientId)
        val where = "${LogTable.COLUMN_CLIENT_ID} = ?"
        val uri = DevinUriHelper.getLogListUri(clientId, isRawQuery = true)
        val cursor = context.contentResolver
            .query(uri, null, where, args.toTypedArray(), "${LogTable.COLUMN_DATE} DESC") ?: return null
        return cursor
    }

    fun clear(context: Context, clientId: String) {
        val uri = DevinUriHelper.getLogListUri(clientId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.contentResolver.delete(uri, null)
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }

    fun getHttpLog(context: Context, clientId: String, logId: LogId): HttpLogDetailData? {
        return try {
            val simpleLog = queryLog(context, clientId, logId.rawId.toString()) ?: return null
            val meta = simpleLog.meta ?: return null
            val metaModel = HttpMetaStruct(meta)
            val detail = metaModel.detail ?: return null
            val operationStatus = HttpLogOperationStatus.fromCode(metaModel.operationStatus, detail.response)

            val url = UrlQuery.create(metaModel.url)
            return HttpLogDetailData(simpleLog.id, detail, operationStatus, url, metaModel.errorSummery)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAllTags(context: Context, clientId: String): Set<String> {
        val uri: Uri = DevinUriHelper.getLogListUri(clientId, isRawQuery = true)
        val where = StringBuilder("${LogTable.COLUMN_CLIENT_ID} = ?").toString()
        val cursor =
            context.contentResolver.query(uri, arrayOf("DISTINCT ${LogTable.COLUMN_TAG}"), where, arrayOf(clientId), null)
                ?: return emptySet()

        val result = HashSet<String>()
        while (cursor.moveToNext()) {
            val element = cursor.getString(0)
            result.add(element)
        }
        cursor.close()
        return result
    }

    fun mapToImageModel(it: LogData): ImageLogData {
        val imageMetaJson = it.meta!!
        val url = imageMetaJson.getString(DevinImageFlagsApi.KEY_IMAGE_URL)
        val status = imageMetaJson.getInt(DevinImageFlagsApi.KEY_IMAGE_STATUS)
        return ImageLogData(it.value, url = url, status = status, it.date)
    }

    fun mapToHttpModel(data: LogData): HttpLogData? {
        data.run {
            val meta = meta ?: return null
            return try {
                val metaJson = meta
                val metaModel = HttpMetaStruct(metaJson)
                val detail = metaModel.detail ?: return null
                val operationStatus = metaModel.operationStatus
                val url = metaModel.url

                val status = HttpLogOperationStatus.fromCode(operationStatus, detail.response)
                HttpLogData(LogId(id), value, url = url, operationStatus = status, date = date, detail.request.method)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun queryLog(
        context: Context,
        clientId: String,
        logId: String,
    ): LogData? {
        val where = "${LogTable.COLUMN_ID} = ? AND ${LogTable.COLUMN_CLIENT_ID} = ?"
        val args = arrayOf(logId, clientId)

        val cursor = context.contentResolver.query(
            DevinUriHelper.getLogListUri(clientId, isRawQuery = true),
            null, where, args, null
        ) ?: return null

        try {
            if (cursor.moveToNext()) {
                return cursor.asLogModel()
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun queryLogList(
        context: Context,
        clientId: String,
        typeId: String? = null,
        tag: OpStringValue? = null,
        value: OpStringValue? = null,
        metaSearch: DevinUriHelper.OpStringParam? = null,
        timeConstraintLessThan: Long? = null,
        page: PageInfo? = null,
    ): List<LogData> {
        try {
            val cursor = queryLogListAsCursor(context, clientId, typeId, tag, value, metaSearch, timeConstraintLessThan, page)
                ?: return emptyList()
            val result = cursor.toLogList()
            cursor.close()
            return result
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }

    private fun queryLogListAsCursor(
        context: Context,
        clientId: String,
        typeId: String? = null,
        tag: OpStringValue? = null,
        value: OpStringValue? = null,
        metaSearch: DevinUriHelper.OpStringParam? = null,
        timeLessThan: Long? = null,
        page: PageInfo? = null,
    ): Cursor? {
        val args = arrayListOf(clientId)
        val where = StringBuilder("${LogTable.COLUMN_CLIENT_ID} = ?").apply {
            if (!typeId.isNullOrEmpty()) {
                args.add(typeId)
                append(" AND ${LogTable.COLUMN_TAG} = ?")
            }
            if (timeLessThan != null) {
                args.add(timeLessThan.toString())
                append(" AND ${LogTable.COLUMN_DATE} < ?")
            }
            if (tag != null) {
                args.add(tag.toSqlStringArgs())
                append(tag.toSqlStringQueryPart(LogTable.COLUMN_TAG))
            }
            if (value != null) {
                args.add(value.toSqlStringArgs())
                append(value.toSqlStringQueryPart(LogTable.COLUMN_VALUE))
            }
        }.toString()
        val uri: Uri = DevinUriHelper.getLogListUri(
            clientId,
            msl1 = metaSearch,
            pageIndex = page?.index,
            itemCount = page?.count,
            isRawQuery = true
        )

        try {
            val cursor = context.contentResolver.query(
                uri,
                null, where, args.toTypedArray(), "${LogTable.COLUMN_DATE} DESC"
            ) ?: return null
            return cursor
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun Cursor.asLogModel(): LogData {
        val cursor = this
        return LogData(
            id = cursor.getLong(0),
            tag = cursor.getString(1),
            value = cursor.getString(2),
            date = cursor.getLong(3),
            meta = cursor.getStringOrNull(4)?.let {
                JsonParser.parseString(it).asJsonObject
            },
            packageId = cursor.getString(5),
            typeId = cursor.getStringOrNull(6)
        )
    }

    fun readLogModel(cursor: Cursor): LogData {
        return cursor.asLogModel()
    }

    private fun OpStringValue.toSqlStringQueryPart(name: String): String {
        return when (this) {
            is OpStringValue.EqualTo -> " And $name = ?"
            is OpStringValue.Contain -> " And $name LIKE ?"
            is OpStringValue.StartWith -> " And $name LIKE ?"
        }
    }

    private fun OpStringValue.toSqlStringArgs(): String {
        return when (this) {
            is OpStringValue.EqualTo -> value
            is OpStringValue.Contain -> "%$value%"
            is OpStringValue.StartWith -> "$value%"
        }
    }

    private fun Cursor.toLogList(): List<LogData> {
        val result = ArrayList<LogData>()
        while (moveToNext()) {
            val element = asLogModel()
            result.add(element)
        }
        return result
    }

    @JvmInline
    private value class HttpMetaStruct(
        val metaJson: JsonObject,
    ) {

        val url: String
            get() = metaJson.getString(DevinHttpFlagsApi.KEY_URL)

        val operationStatus: Int
            get() = metaJson.getInt(DevinHttpFlagsApi.KEY_STATUS_TYPE)

        val detail: HarEntry?
            get() = harFile().log.entries.firstOrNull()

        val errorSummery: String?
            get() = metaJson.optString(DevinHttpFlagsApi.KEY_SUMMERY_OF_ERROR)

        private fun harFile(): HarFile {
            val harJson = metaJson.getAsJsonObject(DevinHttpFlagsApi.KEY_HAR)
            return GsonConverter.instance.fromJson(harJson, HarFile::class.java)
        }
    }

}
