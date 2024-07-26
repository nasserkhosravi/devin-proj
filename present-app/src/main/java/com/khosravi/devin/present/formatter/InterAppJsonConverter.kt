package com.khosravi.devin.present.formatter

import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.getPersianDateTimeFormatted
import com.khosravi.devin.present.mapNotNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

internal object InterAppJsonConverter {

    private const val KEY_ROOT = "logs"
    private const val KEY_JAVA_DATE = "java_date_time"
    private const val KEY_PERSIAN_DATE_TIME = "persian_date_time"

    fun export(
        versionName: String,
        logs: List<LogData>
    ): TextualReport {
        //TODO: Add version name and app name, with package id.
        val root = JSONObject()
            .put("app version name", versionName)

        val jsonGroupedLogs = JSONArray()
        logs.forEach {
            val item = JSONObject()
                .put(LogData.KEY_TAG, it.tag)
                .put(LogData.KEY_MESSAGE, it.value)
                .put(LogData.KEY_DATE, it.date)
                .put(KEY_JAVA_DATE, Date(it.date).toString())
                .put(KEY_PERSIAN_DATE_TIME, getPersianDateTimeFormatted(it.date))
                .put(LogData.KEY_META, it.meta)
                .put(LogData.KEY_CLIENT_ID, it.packageId)
            jsonGroupedLogs.put(item)
        }
        root.put(KEY_ROOT, jsonGroupedLogs)

        return TextualReport("Devin_${Date()}.json", root.toString())
    }

    fun import(json: JSONObject): List<LogData> {
        return json.getJSONArray(KEY_ROOT).mapNotNull {
            if (it is JSONObject) {
                LogData(
                    0L, it.getString(LogData.KEY_TAG),
                    it.getString(LogData.KEY_MESSAGE),
                    it.getLong(LogData.KEY_DATE),
                    it.getString(LogData.KEY_META),
                    it.optString(LogData.KEY_CLIENT_ID) ?: "No client id",
                )
            } else null
        }
    }
}