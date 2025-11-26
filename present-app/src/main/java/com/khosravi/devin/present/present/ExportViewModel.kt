package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import com.khosravi.devin.read.DevinUriHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

                val mainFile = if (exportOptions.withSeparationTagFiles) {
                    //need a zip file for multi file saving.
                    createZipMultiLogFile(calendarProxy, context, clientId, dateConstraint, exportOptions)
                } else {
                    createJsonLogFile(calendarProxy, context, clientId, dateConstraint, exportOptions)
                }
                emit(mainFile)

            }.flowOn(Dispatchers.IO)
        }

        private suspend fun createZipMultiLogFile(
            calendarProxy: CalendarProxy,
            context: Context,
            clientId: String,
            dateConstraint: Long?,
            exportOptions: ExportOptions,
        ): File {
            val fileName = createZipFileNameForExport(calendarProxy.getFormattedCurrentDateTime())
            val zipFile = context.tmpFileForCache(fileName)

            val tagsToExport: Collection<String> = if (exportOptions.tagWhitelist.isNullOrEmpty()) {
                ContentProviderLogsDao.getAllTags(context, clientId)
            } else {
                exportOptions.tagWhitelist.map { it.tagValue }
            }

            // A coroutineScope ensures that all launched jobs complete before this function returns.
            coroutineScope {
                // 1. === WRITE AND FETCH IN A STREAMING MANNER ===
                ZipOutputStream(BufferedOutputStream(zipFile.outputStream())).use { zos ->

                    // Produce a channel of deferred log data. 'async' starts fetching immediately.
                    val logDataProducer = produce(Dispatchers.IO) {
                        // Fetch logs for each tag
                        tagsToExport.forEach { tag ->
                            send(tag to async(Dispatchers.IO) {
                                getLogsAsString(
                                    context, clientId, DevinUriHelper.OpStringValue.EqualTo(tag), dateConstraint, exportOptions
                                )
                            })
                        }
                        // Also fetch the index file if needed
                        if (exportOptions.tagWhitelist.isNullOrEmpty()) {
                            send("index" to async(Dispatchers.IO) {
                                getLogsAsString(context, clientId, null, dateConstraint, exportOptions)
                            })
                        }
                    }

                    // Consume the results as they become available from the channel
                    logDataProducer.consumeEach { (tag, deferredContent) ->
                        try {
                            zos.putNextEntry(ZipEntry("$tag.json"))
                            // await() suspends only until this specific result is ready
                            zos.write(deferredContent.await().toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        } catch (e: Exception) {
                            // Using supervisorScope would be even better to prevent one failure from stopping others.
                            // For now, we just log and the exception will propagate, canceling the scope.
                            Log.e("Export", "Failed to write tag '$tag' to zip", e)
                        }
                    }
                }
            }

            return zipFile
        }

        private fun createJsonLogFile(
            calendarProxy: CalendarProxy,
            context: Context,
            clientId: String,
            dateConstraint: Long?,
            exportOptions: ExportOptions
        ): File {
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
                mainFile.writeText("")
            }
            return mainFile
        }

        /**
         * Queries logs and returns them as a JSON String. This is the workload we can parallelize.
         */
        private fun getLogsAsString(
            context: Context,
            clientId: String,
            tagFilter: DevinUriHelper.OpStringValue?,
            dateConstraint: Long?,
            exportOptions: ExportOptions
        ): String {
            // Use StringWriter to capture the output in memory instead of writing to a file.
            val stringWriter = java.io.StringWriter()
            val bufferedStringWriter = BufferedWriter(stringWriter)

            val query = GetLogsQueryModel(
                null, tagFilter,
                null, null, dateConstraint, null
            )
            ContentProviderLogsDao.queryLogListAsCursor(context, clientId, query)?.use { cursor ->
                writeLogsSeparatedFormat(
                    exportConfig = exportOptions, oWriter = bufferedStringWriter, cursor = cursor,
                    clientId = clientId, logFilter = null
                )
            } ?: return "[]" // Return an empty JSON array if no logs are found.

            return stringWriter.toString()
        }

        fun tagListToFilterFunction(tags: List<String>?): ((LogData) -> Boolean)? {
            if (tags.isNullOrEmpty()) return null
            return { logData ->
                tags.any { it.equals(logData.tag, true) }
            }
        }
    }
}

