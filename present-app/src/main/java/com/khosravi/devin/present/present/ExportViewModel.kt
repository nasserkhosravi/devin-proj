package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khosravi.devin.present.MIME_APP_JSON
import com.khosravi.devin.present.MIME_APP_ZIP
import com.khosravi.devin.present.R
import com.khosravi.devin.present.analytics.Analytics
import com.khosravi.devin.present.analytics.Analytics.ExportDestination
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
import com.khosravi.devin.present.toUriByFileProvider
import com.khosravi.devin.present.tool.PositiveNumber
import com.khosravi.devin.read.DevinUriHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _events = Channel<ExportEvent>(Channel.BUFFERED)

    /** One-shot UI events; each is delivered to a single collector exactly once. */
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    /** Prepared export file waiting for the user to pick a save location. */
    private var saveTmpFile: File? = null

    /** Whether the pending save uses the default option, for analytics once the save finishes. */
    private var isSaveDefaultOption = true

    sealed interface ExportEvent {
        data class LaunchSavePicker(val intent: Intent) : ExportEvent
        data class Share(val uri: Uri, val mimeType: String) : ExportEvent
        data class Message(@StringRes val textRes: Int) : ExportEvent
        data object Close : ExportEvent
    }

    /** Prepares the export file and asks the UI to open the "create document" picker. */
    fun startSave(options: ExportOptions) {
        deleteSaveTmpFile()
        isSaveDefaultOption = isDefaultOption(options)
        launchExportJob(onFailed = { reportExported(isSaveDefaultOption, ExportDestination.SAVE, isSuccessful = false) }) {
            val file = prepareLogsForExport(options).first()
            saveTmpFile = file
            // Progress stays on until the picker result arrives (onSaveLocationPicked), so Share can't delete
            // the pending file. Known gap: if this event is lost (collector cancelled in between) the spinner stays on.
            _events.send(ExportEvent.LaunchSavePicker(createIntentForSave(options.withSeparationTagFiles)))
        }
    }

    /** [uri] is null when the picker was cancelled: drop the prepared file and keep the dialog open. */
    fun onSaveLocationPicked(uri: Uri?) {
        if (uri == null) {
            job?.cancel()
            deleteSaveTmpFile()
            _isExporting.value = false
            return
        }
        val file = saveTmpFile
        saveTmpFile = null
        launchExportJob(onFailed = { reportExported(isSaveDefaultOption, ExportDestination.SAVE, isSuccessful = false) }) {
            val isSaved = try {
                file != null && copyFileToUri(uri, file).first()
            } finally {
                file?.delete()
            }
            _isExporting.value = false
            reportExported(isSaveDefaultOption, ExportDestination.SAVE, isSaved)
            _events.send(ExportEvent.Message(if (isSaved) R.string.msg_save_done else R.string.error_msg_something_went_wrong))
            _events.send(ExportEvent.Close)
        }
    }

    /** Prepares the export file and asks the UI to show the share chooser, then close. */
    fun startShare(options: ExportOptions) {
        deleteSaveTmpFile()
        val isDefault = isDefaultOption(options)
        launchExportJob(onFailed = { reportExported(isDefault, ExportDestination.SHARE, isSuccessful = false) }) {
            val file = prepareLogsForExport(options).first()
            val uri = getContext().toUriByFileProvider(file)
            val mimeType = if (options.withSeparationTagFiles) MIME_APP_ZIP else MIME_APP_JSON
            _isExporting.value = false
            // Success means the share sheet opened, not that the file was sent.
            reportExported(isDefault, ExportDestination.SHARE, isSuccessful = true)
            _events.send(ExportEvent.Share(uri, mimeType))
            _events.send(ExportEvent.Close)
        }
    }

    private fun launchExportJob(onFailed: () -> Unit, block: suspend () -> Unit) {
        job?.cancel()
        _isExporting.value = true
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _isExporting.value = false
                onFailed()
                _events.send(ExportEvent.Message(R.string.error_msg_something_went_wrong))
            }
        }
    }

    private fun isDefaultOption(options: ExportOptions) = options == getExportDefaultOption()

    private fun reportExported(isDefaultOption: Boolean, destination: ExportDestination, isSuccessful: Boolean) {
        Analytics.logsExported(isDefaultOption, destination, isSuccessful)
    }

    private fun deleteSaveTmpFile() {
        saveTmpFile?.delete()
        saveTmpFile = null
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        deleteSaveTmpFile()
    }

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

