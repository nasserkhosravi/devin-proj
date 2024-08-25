package com.khosravi.devin.present

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.DumbDate
import com.khosravi.devin.present.date.DumbTime
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.log.DateLogItemData
import com.khosravi.devin.present.log.HeaderLogDateItem
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
import java.io.FileNotFoundException
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.Calendar
import java.util.Date

const val KEY_DATA = "key_data"
const val MIME_APP_JSON = "application/json"

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