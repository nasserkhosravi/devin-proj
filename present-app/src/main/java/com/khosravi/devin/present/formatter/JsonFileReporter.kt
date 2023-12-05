package com.khosravi.devin.present.formatter

import com.khosravi.devin.write.room.LogTable
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date


internal object JsonFileReporter {

    fun create(
        versionName: String,
        logs: List<LogTable>
    ): TextualReport {
        val root = JSONObject()
            .put("version name", versionName)

        val jsonGroupedLogs = JSONArray()
        logs.forEach {
            val item = JSONObject()
                .put(LogTable.COLUMN_TYPE, it.type)
                .put(LogTable.COLUMN_VALUE, it.value)
            jsonGroupedLogs.put(item)
        }
        root.put("logs", jsonGroupedLogs)

        return TextualReport("DevinShareFile: ${Date()}.json", root.toString())
    }
}