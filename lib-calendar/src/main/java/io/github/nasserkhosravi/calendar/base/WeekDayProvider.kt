package io.github.nasserkhosravi.calendar.base

interface WeekDayProvider {

    val weekDaysName: Map<Int, String>
    val weekDaysShortName: Map<Int, String>
    val weekDaysCalendarIndices: List<Int>

    fun getDayShortNameBy(calendarDay: Int): String {
        return weekDaysShortName[calendarDay]!!
    }

    fun getDayNameBy(calendarDay: Int): String {
        return weekDaysName[calendarDay]!!
    }

}