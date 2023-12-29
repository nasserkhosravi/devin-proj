package com.khosravi.devin.present

import android.content.Context
import android.util.Log
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.DumbDate
import com.khosravi.devin.present.date.DumbTime
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.log.TextLogItemData
import io.github.nasserkhosravi.calendar.iranian.PersianCalendar
import java.util.Calendar
import java.util.Date

const val KEY_DATA = "key_data"

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