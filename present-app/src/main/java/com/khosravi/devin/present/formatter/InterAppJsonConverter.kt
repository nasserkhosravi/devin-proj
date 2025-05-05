package com.khosravi.devin.present.formatter

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.getPersianDateTimeFormatted
import com.khosravi.devin.present.opt
import com.khosravi.devin.present.present.http.GsonConverter
import com.khosravi.devin.write.room.LogTable
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

    //HAR
    private const val KEY_ROOT_HAR = "HAR"


    fun export(
        exporterId: String,
        versionName: String,
        logs: List<LogData>
    ): TextualReport {
        val root = rootApplicationGsonJson(exporterId, versionName)

        val jsonGroupedLogs = JsonArray()
        logs.forEach {
            val item = JsonObject().apply {
                add(KEY_TAG, JsonPrimitive(it.tag))
                add(KEY_MESSAGE, JsonPrimitive(it.value))
                add(KEY_DATE, JsonPrimitive(it.date))
                add(KEY_JAVA_DATE, JsonPrimitive(Date(it.date).toString()))
                add(KEY_PERSIAN_DATE_TIME, JsonPrimitive(getPersianDateTimeFormatted(it.date)))
                add(KEY_META, it.meta)
                add(KEY_CLIENT_ID, JsonPrimitive(it.packageId))
            }
            jsonGroupedLogs.add(item)
        }
        root.add(KEY_ROOT, jsonGroupedLogs)
        val resultString = GsonConverter.instance.toJson(root)
        return TextualReport(createJsonFileName(), resultString)
    }

    fun exportHARContent(
        harEntryJson: JsonObject,
        exporterId: String = BuildConfig.APPLICATION_ID,
        versionName: String = BuildConfig.VERSION_NAME,
    ): String {
        val root = rootApplicationGsonJson(exporterId, versionName)
        val harJson = try {
            GsonConverter.instance.toJsonTree(harEntryJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        root.add(KEY_ROOT_HAR, harJson)
        return root.toString()
    }

    private fun rootApplicationGsonJson(exporterId: String, versionName: String): JsonObject {
        return JsonObject().apply {
            add(KEY_AGENT, JsonObject().apply {
                add(KEY_EXPORTER_ID, JsonPrimitive(exporterId))
                add(KEY_VERSION_NAME, JsonPrimitive(versionName))
            })
        }
    }

    fun createJsonFileName() = "Devin_${Date()}.json"

    fun import(jsonString: String): List<LogData> {
        return try {
            JsonParser.parseString(jsonString).asJsonObject.getAsJsonArray(KEY_ROOT).mapNotNull { json ->
                if (json is JsonObject) {
                    LogData(
                        0L, json.get(KEY_TAG).asString,
                        json.get(KEY_MESSAGE).asString,
                        json.get(KEY_DATE).asLong,
                        json.opt(KEY_META)?.asJsonObject,
                        json.opt(KEY_CLIENT_ID)?.asString ?: "No client id",
                    )
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }

    }
}
