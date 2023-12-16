package io.github.nasserkhosravi.calendar

import io.github.nasserkhosravi.calendar.base.BaseCalendar
import io.github.nasserkhosravi.calendar.base.CalendarDay
import java.util.*


internal fun BaseCalendar.plusDay(day: Int): BaseCalendar {
    require(day >= 0) { "day must not be negative" }
    val calendar: BaseCalendar = clone()
    calendar.add(Calendar.DAY_OF_YEAR, day)
    return createNewInstance(calendar.getTimeInMillis())
}

internal fun BaseCalendar.minusDay(day: Int): BaseCalendar {
    require(day >= 0) { "day must not be negative" }
    val calendar: BaseCalendar = clone()
    calendar.add(Calendar.DAY_OF_YEAR, -day)
    return createNewInstance(calendar.getTimeInMillis())
}

internal fun BaseCalendar.plusYear(year: Int): BaseCalendar {
    require(year >= 0) { "year must not be negative" }
    val calendar = createNewInstance()
    calendar.setDate(getYear() + 1, getMonth(), getDay())
    return calendar
}

internal fun BaseCalendar.minusYear(year: Int): BaseCalendar {
    require(year >= 0) { "year must not be negative" }
    val calendar = createNewInstance()
    calendar.setDate(getYear() - 1, getMonth(), getDay())
    return calendar
}

fun Calendar.isSameYearMonthDate(from: Calendar): Boolean {
    return isSameYear(from) && isSameMonth(from) && isSameDay(from)
}

private fun Calendar.isSameYear(from: Calendar): Boolean = get(Calendar.YEAR) == from.get(Calendar.YEAR)
private fun Calendar.isSameMonth(from: Calendar): Boolean = get(Calendar.MONTH) == from.get(Calendar.MONTH)
private fun Calendar.isSameDay(from: Calendar): Boolean = get(Calendar.DAY_OF_YEAR) == from.get(Calendar.DAY_OF_YEAR)

fun BaseCalendar.asShortString(): String {
    return "y:${getYear()} m:${getMonth()} d:${getDay()}"
}

fun BaseCalendar.isEqualYearMonthDay(to: BaseCalendar): Boolean {
    return getYear() == to.getYear() && getMonth() == to.getMonth() && getDay() == to.getDay() && calendarName == to.calendarName
}

fun CalendarDay.toCalendar(coordinator: BaseCalendar): BaseCalendar {
    return coordinator.createNewInstance(year, month, day)
}

fun BaseCalendar.isBeforeDate(date: BaseCalendar): Boolean {
    return getTimeInMillis() < date.getTimeInMillis()
}

fun BaseCalendar.isAfterDate(date: BaseCalendar): Boolean {
    return getTimeInMillis() > date.getTimeInMillis()
}