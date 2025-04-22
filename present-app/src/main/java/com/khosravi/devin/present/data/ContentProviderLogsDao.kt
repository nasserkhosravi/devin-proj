package com.khosravi.devin.present.data

import android.content.Context
import android.database.Cursor
import android.os.Build
import androidx.core.database.getStringOrNull
import com.google.gson.JsonObject
import com.khosravi.devin.present.data.http.HttpLogData
import com.khosravi.devin.present.data.http.HttpLogDetailData
import com.khosravi.devin.present.data.http.HttpLogOperationStatus
import com.khosravi.devin.present.data.http.UrlQuery
import com.khosravi.devin.present.getInt
import com.khosravi.devin.present.getString
import com.khosravi.devin.present.optString
import com.khosravi.devin.present.present.http.GsonConverter
import com.khosravi.devin.read.DevinImageFlagsApi
import com.khosravi.devin.write.DevinContentProvider
import com.khosravi.devin.write.DevinContentProvider.Companion.uriOfLog
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import com.khosravi.lib.har.HarEntry
import com.khosravi.lib.har.HarFile

object ContentProviderLogsDao {

    fun getAll(context: Context, clientId: String): List<LogData> {
        return provideLogListOf(context, clientId, null)
    }

    private fun provideLogListOf(
        context: Context,
        clientId: String,
        tag: String?,
    ): List<LogData> {
        val cursor =
            context.contentResolver.query(DevinContentProvider.uriOfAllLog(clientId, tag), null, null, arrayOf(clientId), null)
                ?: return emptyList()
        val result = ArrayList<LogData>()
        while (cursor.moveToNext()) {
            val element = cursor.asLogModel()
            result.add(element)
        }
        return result
    }

    fun clear(context: Context, clientId: String) {
        val uri = DevinContentProvider.uriOfAllLog(clientId)
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
            meta = cursor.getStringOrNull(4)?.let {
                GsonConverter.instance.fromJson(it, JsonObject::class.java)
            },
            packageId = cursor.getString(5),
        )
    }

    fun getLogImages(context: Context, clientId: String): List<ImageLogData> {
        //TODO: do direct operation to contentResolver.query.
        return getAll(context, clientId).filter { it.tag == DevinImageFlagsApi.LOG_TAG }.map {
            val imageMetaJson = it.meta!!
            val url = imageMetaJson.getString(DevinImageFlagsApi.KEY_IMAGE_URL)
            val status = imageMetaJson.getInt(DevinImageFlagsApi.KEY_IMAGE_STATUS)
            ImageLogData(it.value, url = url, status = status, it.date)
        }
    }

    private fun LogData.mapToModel(): HttpLogData? {
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

    fun getHttpLogs(context: Context, clientId: String): List<HttpLogData> {
        return provideLogListOf(context, clientId, DevinHttpFlagsApi.LOG_TAG)
            .mapNotNull { it.mapToModel() }

    }

    fun getHttpLog(context: Context, logId: LogId): HttpLogDetailData? {
        val cursor = context.contentResolver
            .query(uriOfLog(logId.rawId), null, null, null, null) ?: return null

        try {
            if (cursor.moveToNext()) {
                val simpleLog = cursor.asLogModel()
                if (simpleLog.tag != DevinHttpFlagsApi.LOG_TAG) return null
                val meta = simpleLog.meta ?: return null
                val metaModel = HttpMetaStruct(meta)
                val detail = metaModel.detail ?: return null
                val operationStatus = HttpLogOperationStatus.fromCode(metaModel.operationStatus, detail.response)

                val url = UrlQuery.create(metaModel.url)
                return HttpLogDetailData(simpleLog.id, detail, operationStatus, url, metaModel.errorSummery)
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
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

        private fun harFile(): HarFile = metaJson.getAsJsonObject(DevinHttpFlagsApi.KEY_HAR).let {
            GsonConverter.instance.fromJson(it, HarFile::class.java)
        }
    }

}
