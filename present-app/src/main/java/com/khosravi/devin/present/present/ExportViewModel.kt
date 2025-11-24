package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.khosravi.devin.present.copyFileToOutputStream
import com.khosravi.devin.present.createJsonFileNameForExport
import com.khosravi.devin.present.createZipFileNameForExport
import com.khosravi.devin.present.data.CacheRepository
import com.khosravi.devin.present.data.ContentProviderLogsDao
import com.khosravi.devin.present.data.LogData
import com.khosravi.devin.present.data.model.GetLogsQueryModel
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.filter.TagFilterItem
import com.khosravi.devin.present.formatter.InterAppJsonConverter.writeLogsFlattenFormat
import com.khosravi.devin.present.formatter.InterAppJsonConverter.writeLogsSeparatedFormat
import com.khosravi.devin.present.requestJsonFileUriToSave
import com.khosravi.devin.present.requestZipFileUriToSave
import com.khosravi.devin.present.tmpFileForCache
import com.khosravi.devin.present.tool.PositiveNumber
import com.khosravi.devin.present.zipFiles
import com.khosravi.devin.read.DevinUriHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import kotlin.time.Duration.Companion.days

class ExportViewModel(
    application: Application,
    private val cacheRepo: CacheRepository,
    private val calendarProxy: CalendarProxy
) : AndroidViewModel(application) {

    fun getExportDefaultOption() = ExportOptions("Default option", null, true, null)

    fun buildExportCustom(tagWhitelist: String?, upToDaysNumber: Int?, withSeparationTagFiles: Boolean): ExportOptions {
        return ExportOptions(
            "Custom option",
            whitelistTextToTags(tagWhitelist),
            withSeparationTagFiles,
            upToDaysNumber?.let { PositiveNumber(it) })
    }

    fun copyFileToUri(uriData: Uri, sourceFile: File): Flow<Boolean> {
        val context = getContext()
        return flow {
            try {
                context.contentResolver.openOutputStream(uriData)?.use { outputStream ->
                    copyFileToOutputStream(sourceFile, outputStream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emit(false)
                return@flow
            }
            emit(true)
        }.flowOn(Dispatchers.IO)
    }

    fun createIntentForSave(needZipFile: Boolean): Intent {
        val fileName = getFormattedCurrentDate(needZipFile)
        return if (needZipFile) requestZipFileUriToSave(fileName)
        else requestJsonFileUriToSave(fileName)
    }

    fun prepareLogsForExport(exportOptions: ExportOptions): Flow<File> {
        val context = getContext()
        val clientId = getSelectedClientIdOrError()
        return Common.prepareLogsForExport(context, clientId, calendarProxy, exportOptions)
    }

    private fun getSelectedClientId() = cacheRepo.getSelectedClientId()

    private fun getSelectedClientIdOrError() = getSelectedClientId()!!

    private fun getContext(): Context = getApplication<Application>().applicationContext

    private fun whitelistTextToTags(tagWhitelist: String?): List<TagFilterItem>? {
        if (tagWhitelist == null) {
            return null
        }
        return if (tagWhitelist.isEmpty()) emptyList()
        else {
            if (tagWhitelist.contains(',')) {
                tagWhitelist.split(',').map { TagFilterItem(it, false) }
            } else {
                listOf(TagFilterItem(tagWhitelist, false))
            }
        }
    }

    private fun getFormattedCurrentDate(needZipFile: Boolean): String {
        val dateTime = calendarProxy.getFormattedCurrentDateTime()
        return if (needZipFile) {
            createZipFileNameForExport(dateTime)
        } else {
            createJsonFileNameForExport(dateTime)
        }
    }

    object Common {

        fun ExportOptions.getUpDaysConstraintAsCurrentMills(mills: Long = System.currentTimeMillis()): Long? {
            return upToDaysNumber?.value?.let {
                mills - it.days.inWholeMicroseconds
            }
        }

        fun buildExportOptionsForSingleFilterItemShare(data: TagFilterItem) =
            ExportOptions("single_share_filter", listOf(data), false, null)

        fun prepareLogsForExport(
            context: Context,
            clientId: String,
            calendarProxy: CalendarProxy,
            exportOptions: ExportOptions
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
                    emit(mainFile)
                }

            }.flowOn(Dispatchers.IO)
        }

        fun writeFileOfZip(
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

        fun writeInTagFormat(
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


        fun tagListToFilterFunction(tags: List<String>?): ((LogData) -> Boolean)? {
            if (tags.isNullOrEmpty()) return null
            return { logData ->
                tags.any { it.equals(logData.tag, true) }
            }
        }
    }
}

