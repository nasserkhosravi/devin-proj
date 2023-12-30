package io.github.nasserkhosravi.calendar.iranian

import io.github.nasserkhosravi.calendar.base.WeekDayProvider
import java.util.Calendar

class IranianWeekProvider : WeekDayProvider {

    private val weekShortDays = mapOf(
        Calendar.SATURDAY to "شنبه",
        Calendar.SUNDAY to "۱‌شنبه",
        Calendar.MONDAY to "۲شنبه",
        Calendar.TUESDAY to "۳‌شنبه",
        Calendar.WEDNESDAY to "۴شنبه",
        Calendar.THURSDAY to "۵‌شنبه",
        Calendar.FRIDAY to "جمعه"
    )

    private val weekVeryShortDays = mapOf(
        Calendar.SATURDAY to "ش",
        Calendar.SUNDAY to "ی",
        Calendar.MONDAY to "د",
        Calendar.TUESDAY to "س",
        Calendar.WEDNESDAY to "چ",
        Calendar.THURSDAY to "پ",
        Calendar.FRIDAY to "ج"
    )

    override val weekDaysName: Map<Int, String> = hashMapOf(
        Calendar.SATURDAY to "شنبه",
        Calendar.SUNDAY to "یک‌شنبه",
        Calendar.MONDAY to "دوشنبه",
        Calendar.TUESDAY to "سه‌شنبه",
        Calendar.WEDNESDAY to "چهارشنبه",
        Calendar.THURSDAY to "پنج‌شنبه",
        Calendar.FRIDAY to "جمعه"
    )
    override val weekDaysShortName: Map<Int, String> = weekVeryShortDays

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