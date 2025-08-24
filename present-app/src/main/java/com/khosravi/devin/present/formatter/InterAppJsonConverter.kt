package com.khosravi.devin.present.formatter

import android.content.Context
import android.database.Cursor
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonWriter
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.createJsonFileNameForExport
import com.khosravi.devin.present.createZipFileNameForExport
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.data.model.GetLogsQueryModel
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.getPersianDateTimeFormatted
import com.khosravi.devin.present.opt
import com.khosravi.devin.present.optIntOrNull
import com.khosravi.devin.present.optString
import com.khosravi.devin.present.optStringOrNull
import com.khosravi.devin.present.present.ExportOptions
import com.khosravi.devin.present.present.ExportOptions.Companion.KEY_UP_TO_DAYS_NUMBER
import com.khosravi.devin.present.present.ExportOptions.Companion.KEY_WHITELIST
import com.khosravi.devin.present.present.ExportOptions.Companion.KEY_WITH_SEPARATION_FILES
import com.khosravi.devin.present.present.http.GsonConverter
import com.khosravi.devin.present.tmpFileForCache
import com.khosravi.devin.present.tool.PositiveNumber
import com.khosravi.devin.present.zipFiles
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.room.LogTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.Date
import kotlin.time.Duration.Companion.days

internal object InterAppJsonConverter {

    //agent
    private const val KEY_OBJ_AGENT = "agent"
    private const val KEY_AGENT_NAME = "name"
    private const val KEY_AGENT_EXPORT_CONFIG = "export_config"
    private const val KEY_AGENT_VERSION_NAME = "version_name"

    //log data
    private const val KEY_LOGS_ROOT = "logs"
    private const val KEY_ID = LogTable.COLUMN_ID
    private const val KEY_TAG = LogTable.COLUMN_TAG
    private const val KEY_VALUE = LogTable.COLUMN_VALUE
    private const val KEY_DATE = LogTable.COLUMN_DATE
    private const val KEY_META = LogTable.COLUMN_META
    private const val KEY_CLIENT_ID = LogTable.COLUMN_CLIENT_ID
    private const val KEY_TYPE_ID = LogTable.COLUMN_TYPE_ID

    //provided presenter data
    private const val KEY_JAVA_DATE_TIME = "java_date_time"
    private const val KEY_PERSIAN_DATE_TIME = "persian_date_time"

    //HAR
    private const val KEY_ROOT_HAR = "HAR"

    fun exportHARContent(
        harEntryJson: JsonObject,
        clientId: String,
        exporterId: String = BuildConfig.APPLICATION_ID,
        versionName: String = BuildConfig.VERSION_NAME,
    ): String {
        val root = rootApplicationJson(exporterId, versionName, clientId, JsonObject().apply {
            addProperty("id", "default HAR")
        })
        val harJson = try {
            GsonConverter.instance.toJsonTree(harEntryJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        root.add(KEY_ROOT_HAR, harJson)
        return root.toString()
    }

    fun import(jsonString: String): List<LogData> {
        return try {
            JsonParser.parseString(jsonString).asJsonObject.getAsJsonArray(KEY_LOGS_ROOT).mapNotNull { json ->
                //TODO: client Id moved to rooApplicationJson
                if (json is JsonObject) {
                    LogData(
                        0L, json.get(KEY_TAG).asString,
                        json.get(KEY_VALUE).asString,
                        json.get(KEY_DATE).asLong,
                        json.opt(KEY_META)?.asJsonObject,
                        json.opt(KEY_CLIENT_ID)?.asString ?: "No client id",
                        json.optString(KEY_TYPE_ID)
                    )
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }

    }


    private fun writeLogsFlattenFormat(
        options: ExportOptions,
        oWriter: Writer,
        cursor: Cursor,
        clientId: String,
        logFilter: ((LogData) -> Boolean)?
    ) {
        createBasicExportStructure(options, oWriter, cursor, clientId) { writer ->
            while (cursor.moveToNext()) {
                val logModel = ContentProviderLogsDao.readLogModel(cursor)
                if (logFilter != null) {
                    if (!logFilter.invoke(logModel)) continue
                }
                writeLogObject(writer, logModel, false)
            }
        }
    }

    private fun writeLogsSeparatedFormat(
        exportConfig: ExportOptions,
        oWriter: Writer,
        cursor: Cursor,
        clientId: String,
        logFilter: ((LogData) -> Boolean)?
    ) {
        createBasicExportStructure(exportConfig, oWriter, cursor, clientId) { writer ->
            while (cursor.moveToNext()) {
                val logModel = ContentProviderLogsDao.readLogModel(cursor)
                if (logFilter != null) {
                    if (!logFilter.invoke(logModel)) continue
                }
                writeLogObject(writer, logModel, true)
            }
        }
    }

    private fun createBasicExportStructure(
        exportConfig: ExportOptions,
        oWriter: Writer,
        cursor: Cursor,
        clientId: String,
        afterStartOfArray: (writer: JsonWriter) -> Unit
    ) {
        val writer = JsonWriter(oWriter)
        val rootObj = rootApplicationJson(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, clientId, exportConfig.toJson())

        cursor.use {
            writer.beginObject() // root {

            val agentElement = rootObj.getAsJsonObject(KEY_OBJ_AGENT)
            writer.name(KEY_OBJ_AGENT)
            writeJsonElement(writer, agentElement)

            writer.name(KEY_LOGS_ROOT)
            writer.beginArray()

            afterStartOfArray(writer)

            writer.endArray() // end JSON array
            writer.endObject() //end root object

            writer.flush()    // flush to output
        }
    }

    private fun writeLogObject(writer: JsonWriter, logModel: LogData, excludeTag: Boolean) {
        writer.beginObject()
        writer.name(KEY_ID).value(logModel.id)
        if (!excludeTag) {
            writer.name(KEY_TAG).value(logModel.tag)
        }
        writer.name(KEY_VALUE).value(logModel.value)
        writer.name(KEY_DATE).value(logModel.date)
        writer.name(KEY_TYPE_ID).value(logModel.typeId)
        logModel.meta?.let {
            writer.name(KEY_META)
            writeJsonElement(writer, it)
        }
        writer.name(KEY_JAVA_DATE_TIME).value(Date(logModel.date).toString())
        writer.name(KEY_PERSIAN_DATE_TIME).value(getPersianDateTimeFormatted(logModel.date))
        writer.endObject()
    }

    private fun rootApplicationJson(
        exporterId: String,
        versionName: String,
        clientId: String,
        exportConfig: JsonObject
    ): JsonObject {
        return JsonObject().apply {
            add(KEY_OBJ_AGENT, JsonObject().apply {
                add(KEY_AGENT_NAME, JsonPrimitive(exporterId))
                add(KEY_AGENT_VERSION_NAME, JsonPrimitive(versionName))
                add(KEY_AGENT_EXPORT_CONFIG, exportConfig)
            })
            addProperty(KEY_CLIENT_ID, clientId)
        }
    }

    private fun writeJsonElement(writer: JsonWriter, element: JsonElement) {
        when {
            element.isJsonNull -> writer.nullValue()
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                when {
                    prim.isBoolean -> writer.value(prim.asBoolean)
                    prim.isNumber -> writer.value(prim.asNumber)
                    prim.isString -> writer.value(prim.asString)
                    else -> writer.nullValue()
                }
            }

            element.isJsonObject -> {
                writer.beginObject()
                val obj = element.asJsonObject
                for ((key, value) in obj.entrySet()) {
                    writer.name(key)
                    writeJsonElement(writer, value)
                }
                writer.endObject()
            }

            element.isJsonArray -> {
                writer.beginArray()
                val arr = element.asJsonArray
                for (item in arr) {
                    writeJsonElement(writer, item)
                }
                writer.endArray()
            }
        }
    }


    fun buildExportOptionsFromJson(id: String, json: JSONObject): ExportOptions {
        return ExportOptions(
            id,
            json.optStringOrNull(KEY_WHITELIST)?.let { decodeWhitelistTextToTags(it) },
            json.optBoolean(KEY_WITH_SEPARATION_FILES, false),
            json.optIntOrNull(KEY_UP_TO_DAYS_NUMBER)?.let { PositiveNumber(it) }
        )

    }

    fun decodeWhitelistTextToTags(tagWhitelist: String?): List<TagFilterItem>? {
        if (tagWhitelist == null) {
            return null
        }
        return if (tagWhitelist.isEmpty()) emptyList()
        else {
            if (tagWhitelist.contains(',')) {
                tagWhitelist.split(',').map { TagFilterItem(it) }
            } else {
                listOf(TagFilterItem(tagWhitelist))
            }
        }
    }

    fun codeTagsToWhitelist(tags: List<TagFilterItem>?): String? {
        return tags?.joinToString(",") { it.id }
    }

    fun prepareLogsForExport(
        context: Context, clientId: String, exportOptions: ExportOptions,
        calendarProxy: CalendarProxy
    ): Flow<File> {

        return flow {
            val dateConstraint = exportOptions.getUpDaysConstraintAsCurrentMills()

            if (exportOptions.withSeparationTagFiles) {
                //need a zip file for multi file saving.
                val fileName = createZipFileNameForExport(calendarProxy.getFormattedCurrentDateTime())
                val mainFile = context.tmpFileForCache(fileName)
                val filesInZip = ArrayList<File>()
                if (exportOptions.tagWhitelist.isNullOrEmpty()) {

                    ContentProviderLogsDao.getAllTags(context, clientId).forEach { tag ->
                        val newFile = context.tmpFileForCache("filter_$tag.json")
                        writeFileOfZip(
                            context, clientId, DevinUriHelper.OpStringValue.EqualTo(tag),
                            dateConstraint, newFile, exportOptions, filesInZip
                        )
                    }
                    val indexFile = context.tmpFileForCache("index.json")
                    writeFileOfZip(context, clientId, null, dateConstraint, indexFile, exportOptions, filesInZip)
                    zipFiles(filesInZip, mainFile)
                    filesInZip.forEach { it.delete() }

                    emit(mainFile)
                } else {
                    exportOptions.tagWhitelist.forEach { tag ->
                        val newFile = context.tmpFileForCache("filter_$tag.json")

                        val cursor = ContentProviderLogsDao.queryLogListAsCursor(
                            context, clientId,
                            GetLogsQueryModel(
                                null, DevinUriHelper.OpStringValue.EqualTo(tag.tagValue),
                                null, null, dateConstraint, null
                            )
                        )
                        if (cursor != null) {
                            writeInTagFormat(mainFile, exportOptions, cursor, clientId)
                            cursor.close()
                            filesInZip.add(newFile)
                        }
                    }
                    zipFiles(filesInZip, mainFile)
                    filesInZip.forEach { it.delete() }

                    emit(mainFile)
                }

            } else {
                val fileName = createJsonFileNameForExport(calendarProxy.getFormattedCurrentDateTime())
                val mainFile = context.tmpFileForCache(fileName)
                makeJsonFile(context, clientId, dateConstraint, mainFile, exportOptions)
                emit(mainFile)
            }

        }.flowOn(Dispatchers.IO)
    }

    private fun makeJsonFile(
        context: Context,
        clientId: String,
        dateConstraint: Long?,
        mainFile: File,
        exportOptions: ExportOptions
    ) {
        // all logs in one file.
        val cursor = ContentProviderLogsDao.queryLogListAsCursor(
            context, clientId,
            GetLogsQueryModel(null, null, null, null, dateConstraint, null)
        )

        if (cursor != null) {
            val writer: Writer = OutputStreamWriter(FileOutputStream(mainFile), "UTF-8")
            // Optional: buffer it for efficient writes
            val bufferedWriter: Writer = BufferedWriter(writer)
            writer.use {
                writeLogsFlattenFormat(
                    exportOptions, bufferedWriter, cursor,
                    clientId, tagListToFilterFunction(exportOptions.tagWhitelist?.map { it.tagValue })
                )
            }
            cursor.close()
        } else {
            throw IllegalStateException("Cannot get logs for sharing")
        }
    }

    private fun writeFileOfZip(
        context: Context,
        clientId: String,
        tagFilter: DevinUriHelper.OpStringValue.EqualTo?,
        dateConstraint: Long?,
        newFile: File,
        exportOptions: ExportOptions,
        filesInZip: ArrayList<File>
    ) {
        val cursor = ContentProviderLogsDao.queryLogListAsCursor(
            context, clientId,
            GetLogsQueryModel(
                null, tagFilter,
                null, null, dateConstraint, null
            )
        )
        if (cursor != null) {
            writeInTagFormat(newFile, exportOptions, cursor, clientId)
            cursor.close()
            filesInZip.add(newFile)
        }
    }


    private fun writeInTagFormat(
        file: File,
        exportOptions: ExportOptions,
        cursor: Cursor,
        clientId: String
    ) {
        val writer: Writer = OutputStreamWriter(FileOutputStream(file), "UTF-8")
        // Optional: buffer it for efficient writes
        val bufferedWriter: Writer = BufferedWriter(writer)
        writer.use {
            writeLogsSeparatedFormat(exportOptions, bufferedWriter, cursor, clientId, null)
        }
    }

    private fun ExportOptions.getUpDaysConstraintAsCurrentMills(mills: Long = System.currentTimeMillis()): Long? {
        return upToDaysNumber?.value?.let {
            mills - it.days.inWholeMilliseconds
        }
    }

    private fun tagListToFilterFunction(tags: List<String>?): ((LogData) -> Boolean)? {
        if (tags.isNullOrEmpty()) return null
        return { logData ->
            tags.any { it.equals(logData.tag, true) }
        }
    }
}
