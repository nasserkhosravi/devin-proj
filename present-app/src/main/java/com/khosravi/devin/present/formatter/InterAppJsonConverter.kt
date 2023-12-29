package com.khosravi.devin.present.formatter

import com.khosravi.devin.present.data.LogTable
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
        logs: List<LogTable>
    ): TextualReport {
        val root = JSONObject()
            .put("app version name", versionName)

        val jsonGroupedLogs = JSONArray()
        logs.forEach {
            val item = JSONObject()
                .put(LogTable.KEY_TAG, it.tag)
                .put(LogTable.KEY_MESSAGE, it.value)
                .put(LogTable.KEY_DATE, it.date)
                .put(KEY_JAVA_DATE, Date(it.date).toString())
                .put(KEY_PERSIAN_DATE_TIME, getPersianDateTimeFormatted(it.date))
                .put(LogTable.KEY_META, it.meta)
            jsonGroupedLogs.put(item)
        }
        root.put(KEY_ROOT, jsonGroupedLogs)

        return TextualReport("Devin_${Date()}.json", root.toString())
    }

    fun import(json: JSONObject): List<LogTable> {
        return json.getJSONArray(KEY_ROOT).mapNotNull {
            if (it is JSONObject) {
                LogTable(
                    0L, it.getString(LogTable.KEY_TAG),
                    it.getString(LogTable.KEY_MESSAGE),
                    it.getLong(LogTable.KEY_DATE),
                    it.getString(LogTable.KEY_META)
                )
            } else null
        }
    }
}