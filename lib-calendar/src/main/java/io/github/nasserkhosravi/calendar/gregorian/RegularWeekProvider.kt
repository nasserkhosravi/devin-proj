package io.github.nasserkhosravi.calendar.gregorian

import io.github.nasserkhosravi.calendar.base.WeekDayProvider
import java.util.Calendar

class RegularWeekProvider : WeekDayProvider {

    override val weekDaysName: Map<Int, String> = mapOf(
        Calendar.SATURDAY to "Saturday",
        Calendar.SUNDAY to "Sunday",
        Calendar.MONDAY to "Monday",
        Calendar.TUESDAY to "Tuesday",
        Calendar.WEDNESDAY to "Wednesday",
        Calendar.THURSDAY to "Thursday",
        Calendar.FRIDAY to "Friday"
    )

    override val weekDaysShortName: Map<Int, String> = mapOf(
        Calendar.SATURDAY to "Sat",
        Calendar.SUNDAY to "Sun",
        Calendar.MONDAY to "Mon",
        Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu",
        Calendar.FRIDAY to "Fri"
    )

    override val weekDaysCalendarIndices: List<Int> = listOf(
        Calendar.SATURDAY,
        Calendar.SUNDAY,
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY
    )

}