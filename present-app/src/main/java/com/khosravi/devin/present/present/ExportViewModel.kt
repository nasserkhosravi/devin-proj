package com.khosravi.devin.present.present

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.khosravi.devin.present.copyFileToOutputStream
import com.khosravi.devin.present.createJsonFileNameForExport
import com.khosravi.devin.present.createZipFileNameForExport
import com.khosravi.devin.present.data.CacheRepository
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.formatter.InterAppJsonConverter
import com.khosravi.devin.present.requestJsonFileUriToSave
import com.khosravi.devin.present.requestZipFileUriToSave
import com.khosravi.devin.present.tool.PositiveNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class ExportViewModel(
    application: Application,
    private val cacheRepo: CacheRepository,
    private val calendarProxy: CalendarProxy
) : AndroidViewModel(application) {

    fun getExportDefaultOption() = ExportOptions("Default option", null, true, null)

    fun buildExportCustom(tagWhitelist: String?, upToDaysNumber: Int?, withSeparationTagFiles: Boolean): ExportOptions {
        return ExportOptions(
            "Custom option",
            InterAppJsonConverter.decodeWhitelistTextToTags(tagWhitelist),
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
        return InterAppJsonConverter.prepareLogsForExport(context, clientId, exportOptions, calendarProxy)
    }

    private fun getSelectedClientId() = cacheRepo.getSelectedClientId()

    private fun getSelectedClientIdOrError() = getSelectedClientId()!!

    private fun getContext(): Context = getApplication<Application>().applicationContext

    private fun getFormattedCurrentDate(needZipFile: Boolean): String {
        val dateTime = calendarProxy.getFormattedCurrentDateTime()
        return if (needZipFile) {
            createZipFileNameForExport(dateTime)
        } else {
            createJsonFileNameForExport(dateTime)
        }
    }

}

