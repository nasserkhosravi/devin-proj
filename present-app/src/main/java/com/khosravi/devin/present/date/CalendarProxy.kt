package com.khosravi.devin.present.date

import io.github.nasserkhosravi.calendar.base.BaseCalendar
import io.github.nasserkhosravi.calendar.gregorian.EnglishCalendar
import io.github.nasserkhosravi.calendar.iranian.PersianCalendar
import java.util.Calendar
import java.util.Date

class CalendarProxy(implType: CalendarType) {

    var calendar = createCalendarImpl(implType)
        private set

    private fun createCalendarImpl(implType: CalendarType): BaseCalendar {
        return when (implType) {
            CalendarType.PERSIAN -> PersianCalendar.getInstance()
            CalendarType.GREGORIAN -> EnglishCalendar.getInstance()
        }
    }

    fun changeCalendar(type: CalendarType) {
        calendar = createCalendarImpl(type)
    }

    //TODO: Move [initIfNeed] to somewhere else,
    fun initIfNeed(date: DatePresent): DatePresent {
        val dumbed = date.dumbed
        if (dumbed == null) {
            val calendar = calendar.createNewInstance(date.timestamp)
            date.dumbed = DumbDate(calendar.getYear(), calendar.getMonth(), calendar.getDay())
        }
        return date
    }

    fun initIfNeed(date: TimePresent): TimePresent {
        val dumbed = date.dumbed
        if (dumbed == null) {
            val calendar = Calendar.getInstance().apply {
                time = Date(date.timestamp)
            }
            date.dumbed = DumbTime(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND))
        }
        return date
    }

    fun initIfNeed(dateAndTimePresent: DateTimePresent): DateTimePresent {
        val dumbed = dateAndTimePresent.dumbed
        if (dumbed == null) {
            val calendar = calendar.createNewInstance(dateAndTimePresent.timestamp)
            dateAndTimePresent.dumbed = DumbDateTime(
                DumbDate(calendar.getYear(), calendar.getMonth(), calendar.getDay()),
                DumbTime(calendar[Calendar.HOUR_OF_DAY], calendar[Calendar.MINUTE], calendar[Calendar.SECOND])
            )
        }
        return dateAndTimePresent
    }

}