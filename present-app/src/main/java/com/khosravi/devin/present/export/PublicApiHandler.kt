package com.khosravi.devin.present.export

import android.content.Context
import android.content.Intent
import android.util.Log
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.present.ExportOptions
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject

class PublicApiHandler @Inject constructor(private val calendar: CalendarProxy) {

    fun createAction(intent: Intent?): PublicApiAction? {
        val intent = intent ?: return null
        val clientId = intent.getStringExtra(ExportApiConstant.KEY_CLIENT_ID)
        if (clientId.isNullOrEmpty()) return null

        val exportUrl = intent.getStringExtra(ExportApiConstant.KEY_EXPORT_URL)
        if (!exportUrl.isNullOrEmpty()) {
            return PublicApiAction.ExportOnlyHttp(clientId, exportUrl)
        }
        return null
    }

    fun process(action: PublicApiAction, context: Context): Flow<Boolean> = flow {
        Log.i(TAG, "start action in scope")
        when (action) {
            is PublicApiAction.ExportOnlyHttp -> {
                val url = action.exportUrl
                val exportOptions =
                    ExportOptions(context.packageName, listOf(TagFilterItem(DevinHttpFlagsApi.LOG_TAG)), false, null)
                val logFile = InterAppJsonConverter
                    .prepareLogsForExport(context, action.clientId, exportOptions, calendar).firstOrNull()
                if (logFile == null) {
                    Log.e(TAG, "can not create log file for export")
                    emit(false)
                    return@flow
                }

                val request = buildRequest(logFile, null, url)
                if (request==null){
                    Log.e(TAG, "can not create request for export")
                    emit(false)
                    return@flow
                }
                val resString = makeRequest(request)
                Log.d(TAG, "response: $resString")
                emit(true)
            }
        }
    }.catch { e ->
        Log.e(TAG, "Exception on endpoint call of ${(action as? PublicApiAction.ExportOnlyHttp)?.exportUrl}", e)
        emit(false)
    }.flowOn(Dispatchers.IO)


    private fun makeRequest(request: Request): String? {
        val call = OkHttpClient.Builder()
            .build()
            .newCall(request)

        val response = call.execute()

        if (!response.isSuccessful) {
            throw IOException("Unexpected code $response");
        }

        val resString = response.body?.string()
        return resString
    }

    private fun buildRequest(
        logFile: File,
        headers: Map<String, Any>?,
        url: String
    ): Request? {

        val mediaType = when (logFile.extension) {
            "json" -> "application/json".toMediaType()
            "zip" -> "application/zip".toMediaType()
            else -> null
        } ?: return null

        val fileBody: RequestBody = logFile.asRequestBody(mediaType)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", logFile.getName(), fileBody)
            .build()

        return Request.Builder().url(url).post(requestBody).headers(headers.toHeadersOrEmpty()).build()
    }

    private fun Map<String, Any>?.toHeadersOrEmpty(): Headers {
        val fHeaders = Headers.Builder()
        if (this == null) return fHeaders.build()

        forEach { (t, u) ->
            fHeaders.add(t, u as String)
        }
        return fHeaders.build()
    }

    companion object {
        private const val TAG = "devin-export"
    }

}

object ExportApiConstant {

    const val KEY_CLIENT_ID = "clientId"
    const val KEY_EXPORT_URL = "exportUrl"

}