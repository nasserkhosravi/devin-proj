package com.khosravi.devin.present

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.DumbDate
import com.khosravi.devin.present.date.DumbTime
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.formatter.TextualReport
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.HeaderLogDateItem
import com.khosravi.devin.present.log.HttpLogItemData
import com.khosravi.devin.present.log.HttpLogItemView
import com.khosravi.devin.present.log.ImageLogItem
import com.khosravi.devin.present.log.ImageLogItemData
import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItem
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.TextLogItem
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.log.TextLogSubItem
import com.mikepenz.fastadapter.GenericItem
import io.github.nasserkhosravi.calendar.iranian.PersianCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.FileNotFoundException
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.Calendar
import java.util.Date

const val KEY_DATA = "key_data"
const val MIME_APP_JSON = "application/json"
const val MIME_APP_ZIP = "application/zip"

fun getPersianDateTimeFormatted(timestamp: Long): String {
    val persianCalendar = PersianCalendar.getInstance().createNewInstance(timestamp)
    val calendar = Calendar.getInstance().apply {
        time = Date(timestamp)
    }

    val dumbTime = DumbTime(
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        calendar.get(Calendar.SECOND)
    )
    val dumbDate = DumbDate(persianCalendar.getYear(), persianCalendar.getMonth() + 1, persianCalendar.getDay())
    return "${DatePresent.getFormatted(dumbDate)} ${TimePresent.getFormatted(dumbTime)}"
}

fun TextLogItemData.getLogColor(context: Context): Int {
    return when (logLevel) {
        Log.DEBUG -> context.getColor(R.color.colorLogDebug)
        Log.ERROR -> context.getColor(R.color.colorLogError)
        Log.INFO -> context.getColor(R.color.colorLogInfo)
        Log.WARN -> context.getColor(R.color.colorLogWarning)
        else -> context.getColor(R.color.colorOnSurface)
    }
}

fun List<LogItemData>.toItemViewHolder(calendar: CalendarProxy): List<GenericItem> {
    return map { item ->
        when (item) {
            is DateLogItemData -> HeaderLogDateItem(calendar, item)
            is TextLogItemData -> TextLogItem(calendar, item)
            is HttpLogItemData -> HttpLogItemView(calendar, item)
            is ImageLogItemData -> ImageLogItem(calendar, item)
            is ReplicatedTextLogItemData -> ReplicatedTextLogItem(calendar, item).apply {
                subItems = data.list.map { TextLogSubItem(calendar, it, this) }.toMutableList()
            }

        }
    }
}


@SuppressLint("Recycle")
fun ContentResolver.writeTextToUri(uri: Uri, text: String): Boolean {
    try {
        val outputStream = openOutputStream(uri) ?: return false
        outputStream.writeTextAndClose(text)
        return true
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
        return false
    }
}


fun humanReadableByteCountSI(bytes: Long): String {
    var mBytes = bytes
    if (-1000 < mBytes && mBytes < 1000) {
        return "$mBytes B"
    }
    val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
    while (mBytes <= -999950 || mBytes >= 999950) {
        mBytes /= 1000
        ci.next()
    }
    return String.format("%.1f %cB", mBytes / 1000.0, ci.current())
}

fun Context.createCacheShareFile(textualReport: TextualReport): Uri {
    val file = tmpFileForCache(textualReport.fileName)
    file.printWriter().use { out ->
        out.print(textualReport.content)
    }
    return toUriByFileProvider(file)
}

fun AppCompatActivity.createFlowForExportFileIntentResult(content: String, activityResult: ActivityResult): Flow<Unit> {
    val returnedIntent = activityResult.data
    val uriData = returnedIntent?.data
    if (activityResult.resultCode == AppCompatActivity.RESULT_OK && returnedIntent != null && uriData != null) {
        return flowOf(uriData)
            .flowOn(Dispatchers.Default)
            .map { contentResolver.writeTextToUri(uriData, content) }
            .flowOn(Dispatchers.Main)
            .onEach { uriWriteResult ->
                val msg = if (uriWriteResult) getString(R.string.msg_save_done)
                else getString(R.string.error_msg_something_went_wrong)
                Toast.makeText(this@createFlowForExportFileIntentResult, msg, Toast.LENGTH_LONG).show()
            }.map { Unit }
    }
    return emptyFlow()
}

fun Context.setClipboardSafe(text: String): Boolean {
    return try {
        setClipboard(text)
        true
    } catch (e: Exception) {
        Toast.makeText(this, getString(R.string.msg_failed_to_copy), Toast.LENGTH_SHORT).show()
        false
    }
}

fun requestJsonFileUriToSave(fileName: String): Intent {
    return writeOrSaveFileIntent(fileName, MIME_APP_JSON)
}

fun requestZipFileUriToSave(fileName: String): Intent {
    return writeOrSaveFileIntent(fileName, MIME_APP_ZIP)
}

fun createZipFileNameForExport(dateData: String) = "Devin_${dateData}.zip"

fun createJsonFileNameForExport(dateData: String) = "Devin_${dateData}.json"

fun JsonObject.opt(key: String): JsonElement? {
    if (has(key)) {
        return get(key)
    }
    return null
}