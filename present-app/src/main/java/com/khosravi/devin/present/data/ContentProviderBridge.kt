package com.khosravi.devin.present.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.khosravi.devin.present.createJsonFileNameForExport
import com.khosravi.devin.present.data.model.GetLogsQueryModel
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.formatter.InterAppJsonConverter.getUpDaysConstraintAsCurrentMills
import com.khosravi.devin.present.formatter.InterAppJsonConverter.writeLogsFlattenFormat
import com.khosravi.devin.present.present.ExportOptions
import com.khosravi.devin.present.tool.PositiveNumber
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi
import com.khosravi.devin.write.room.LogTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer


object ContentProviderBridge {
    private const val TAG = "ContentProviderBridge"
    private const val FILE_NAME = "all_http_logs"
    private const val KEY_AutoSaveHttpLog = "autoSaveHttpLogs"


    private val scope = CoroutineScope(Dispatchers.IO)
    private var autoSaveJob: Job? = null

    private var isAutoSaveHttpLogs: Boolean? = null
    private var logDirectory: File? = null
    private var presenterConfig: JSONObject? = null

    fun onInsertLog(logUri: Uri, context: Context, logTable: LogTable) {
        Log.d(TAG, "onInsertLog $logUri")
        val clientId = logTable.clientId
        if (logTable.typeId != DevinHttpFlagsApi.TYPE_ID) {
            return
        }
        if (presenterConfig == null) {
            val client = ClientContentProvider.getClient(context, clientId) ?: return
            presenterConfig = client.presenterConfig ?: JSONObject()
        }

        if (isAutoSaveHttpLogs == null) {
            isAutoSaveHttpLogs = presenterConfig?.optBoolean(KEY_AutoSaveHttpLog, false) ?: return
        }
        if (isAutoSaveHttpLogs == false) return
        if (logDirectory == null) {
            logDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            // Check if the directory exists. Create it if it doesn't.
            if (logDirectory == null) {
                Log.d("xosro", "Error: Could not get external documents directory.")
                return
            }
            if (!logDirectory!!.exists()) {
                if (!logDirectory!!.mkdirs()) {
                    Log.d("xosro", "Error: Could not create log directory.")
                    return
                }
            }
        }


        autoSaveJob?.cancel()

        autoSaveJob = scope.launch {
            delay(3000)

            val exportOptions = ExportOptions(
                KEY_AutoSaveHttpLog,
                listOf(TagFilterItem(DevinHttpFlagsApi.LOG_TAG)),
                false, PositiveNumber(1)
            )

            val fileName = createJsonFileNameForExport(FILE_NAME)
            val mainFile = File(logDirectory, fileName)
            if (mainFile.exists()) {
                mainFile.delete()
            }

            val cursor = ContentProviderLogsDao.queryLogListAsCursor(
                context, logTable.clientId,
                GetLogsQueryModel(
                    null, tag = DevinUriHelper.OpStringValue.EqualTo(DevinHttpFlagsApi.LOG_TAG),
                    null, null, timeLessThan =  exportOptions.getUpDaysConstraintAsCurrentMills(), null
                )
            )

            if (cursor != null) {
                val writer: Writer = OutputStreamWriter(FileOutputStream(mainFile), "UTF-8")
                val bufferedWriter: Writer = BufferedWriter(writer)
                writer.use {
                    writeLogsFlattenFormat(
                        exportOptions, bufferedWriter, cursor, clientId, null
                    )
                }
                cursor.close()
                Log.d(TAG, "devin-write $KEY_AutoSaveHttpLog successfully: $mainFile ")
            } else {
                throw IllegalStateException("Cannot get logs for sharing")
            }

        }
    }

}