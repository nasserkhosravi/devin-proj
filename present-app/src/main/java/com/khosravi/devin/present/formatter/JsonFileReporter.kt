package com.khosravi.devin.present.formatter

import com.khosravi.devin.present.data.LogTable
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
                .put("type", it.type)
                .put("value", it.value)
            jsonGroupedLogs.put(item)
        }
        root.put("logs", jsonGroupedLogs)

        return TextualReport("DevinShareFile: ${Date()}.json", root.toString())
    }
}