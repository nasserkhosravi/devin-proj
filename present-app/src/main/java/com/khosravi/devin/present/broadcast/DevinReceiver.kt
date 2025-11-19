package com.khosravi.devin.present.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.khosravi.devin.present.PresentApplication
import com.khosravi.devin.present.api.DevinIntent
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.optStringOrNull
import com.khosravi.devin.present.toMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject


/**
 * Broadcast Receiver to provide a API for exporting logs.
 *
 * example to run from ADB.
 * adb shell "am broadcast -a devin.intent.action.EXPORT_LOGS -p com.khosravi.devin.present --es notify '$(cat devin_export_config.json)'"
 *
 * devin_config_export.json example
 * {
 *   "clientId": "com.khosravi.sample.devin",
 *   "export": {
 *     "tagWhitelist": "okhttp",
 *     "withSeparationTagFiles": false,
 *     "upToDaysNumber": 1
 *   },
 *   "dest": {
 *     "name": "endpoint",
 *     "url": "http://10.0.2.2/mysite/devin_file_receiver.php",
 *     "headers": {
 *       "myToken": "xxx"
 *     }
 *   }
 * }
 */
class DevinReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DevinReceiver"
    }

    @Inject
    lateinit var calendar: CalendarProxy
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive: action:${intent?.action} extras:${intent?.extras} ")
        context ?: return

        if (intent?.action == DevinIntent.ACTION_EXPORT_LOGS) {
            getAppComponent(context).inject(this)
            handleIt(intent, context)
        }
    }

    private fun handleIt(intent: Intent, context: Context) {
        val configJson = intent.getStringExtra("notify")?.let { JSONObject(it) } ?: return
        val clientId = configJson.optStringOrNull("clientId") ?: return
        val methodJson = configJson.optJSONObject("dest") ?: return
        val url = methodJson.optStringOrNull("url") ?: return
        val headers = methodJson.optJSONObject("headers")?.toMap()

        val exportOptions = configJson.optJSONObject("export")?.let {
            InterAppJsonConverter.buildExportOptionsFromJson(TAG, it)
        } ?: return

        scope.launch {
            val logFile = InterAppJsonConverter.prepareLogsForExport(context, clientId, exportOptions, calendar).firstOrNull()
            if (logFile == null) {
                Log.e(TAG, "can create log file for export")
                return@launch
            }

            try {
                val request = buildRequest(logFile, headers, url) ?: return@launch
                val resString = makeRequest(request)
                Log.d(TAG, "response: $resString")

            } catch (e: Exception) {
                Log.e(TAG, "Exception on endpoint call of $url", e)
            }
        }
    }

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

    private fun getAppComponent(context: Context) = (context.applicationContext as PresentApplication).appComponent
}

