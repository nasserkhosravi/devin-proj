package io.github.nasserkhosravi.calendar.base

import org.jetbrains.annotations.Nullable
import java.io.Serializable
import java.util.TimeZone

interface BaseCalendar : Serializable {

    val calendarName: String

    operator fun set(field: Int, value: Int)

    fun setTimeInMillis(millis: Long)

    fun setTimeZone(zone: TimeZone)

    fun setDate(year: Int, month: Int, day: Int)

    operator fun get(value: Int): Int

    fun add(field: Int, amount: Int)

    fun getActualMaximum(value: Int): Int

    fun getYear(): Int

    fun getMonth(): Int

    fun getDay(): Int

    fun getDayOfWeek(): Int

    fun getTimeInMillis(): Long

    fun getTimeZone(): TimeZone

    fun getMonthName(): String

    fun getWeekDayName(): String

    fun getWeekDayNameShortType(): String

    fun getLongDate(): String

    fun getShortDateFormat(): String

    fun getShortDate(): String

    fun createNewInstance(year: Int, month: Int, day: Int): BaseCalendar

    fun createNewInstance(): BaseCalendar

    fun createNewInstance(time: Long): BaseCalendar

    fun minusYear(amount: Int): BaseCalendar

    fun plusYear(amount: Int): BaseCalendar

    fun minusDay(amount: Int): BaseCalendar

    fun plusDay(amount: Int): BaseCalendar

    fun clone(): BaseCalendar

    fun getStartOfYearDate(): BaseCalendar

    fun getEndOfYearDate(): BaseCalendar

    @Nullable
    fun parseDate(date: String): BaseCalendar

    fun isAfterDate(date: YearMonthDay): Boolean

    fun isAfterDate(date: BaseCalendar): Boolean

    fun isBeforeDate(date: BaseCalendar): Boolean

    fun isWeekend(year: Int, month: Int, day: Int): Boolean

    fun getFirstDayOfWeek(): Int

    fun getMonthDaysCountOf(month: Int, year: Int): Int

    fun getMonthNameInfo(): MonthProvider

    fun getWeekNameInfo(): WeekDayProvider

}