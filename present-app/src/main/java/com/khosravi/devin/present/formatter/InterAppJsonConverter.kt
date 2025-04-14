package com.khosravi.devin.present.formatter

import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.getPersianDateTimeFormatted
import com.khosravi.devin.present.mapNotNull
import com.khosravi.devin.write.room.LogTable
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

internal object InterAppJsonConverter {

    //agent
    private const val KEY_AGENT = "agent"
    private const val KEY_EXPORTER_ID = "name"
    private const val KEY_VERSION_NAME = "version_name"

    //log data
    private const val KEY_ROOT = "logs"
    private const val KEY_TAG = "tag"
    private const val KEY_MESSAGE = "message"
    private const val KEY_DATE = "date"
    private const val KEY_META = "meta"
    private const val KEY_CLIENT_ID = LogTable.COLUMN_CLIENT_ID

    //provided presenter data
    private const val KEY_JAVA_DATE = "java_date_time"
    private const val KEY_PERSIAN_DATE_TIME = "persian_date_time"

    fun export(
        exporterId: String,
        versionName: String,
        logs: List<LogData>
    ): TextualReport {
        //TODO: Add version name and app name, with package id.
        val root = rootApplicationJson(exporterId, versionName)

        val jsonGroupedLogs = JSONArray()
        logs.forEach {
            val item = JSONObject()
                .put(KEY_TAG, it.tag)
                .put(KEY_MESSAGE, it.value)
                .put(KEY_DATE, it.date)
                .put(KEY_JAVA_DATE, Date(it.date).toString())
                .put(KEY_PERSIAN_DATE_TIME, getPersianDateTimeFormatted(it.date))
                .put(KEY_META, it.meta)
                .put(KEY_CLIENT_ID, it.packageId)
            jsonGroupedLogs.put(item)
        }
        root.put(KEY_ROOT, jsonGroupedLogs)

        return TextualReport(createJsonFileName(), root.toString())
    }

    fun exportHARContent(
        harEntryJson: JSONObject,
        exporterId: String = BuildConfig.APPLICATION_ID,
        versionName: String = BuildConfig.VERSION_NAME,
    ): String {
        val root = rootApplicationJson(exporterId, versionName)
        val jsonGroupedLogs = JSONObject().put("log", harEntryJson)
        root.put("detail", jsonGroupedLogs)
        return root.toString()
    }

    private fun rootApplicationJson(exporterId: String, versionName: String): JSONObject = JSONObject()
        .put(KEY_AGENT, JSONObject().apply {
            put(KEY_EXPORTER_ID, exporterId)
            put(KEY_VERSION_NAME, versionName)
        })

    fun createJsonFileName() = "Devin_${Date()}.json"

    fun import(json: JSONObject): List<LogData> {
        return json.getJSONArray(KEY_ROOT).mapNotNull {
            if (it is JSONObject) {
                LogData(
                    0L, it.getString(KEY_TAG),
                    it.getString(KEY_MESSAGE),
                    it.getLong(KEY_DATE),
                    it.getString(KEY_META),
                    it.optString(KEY_CLIENT_ID) ?: "No client id",
                )
            } else null
        }
    }
}