package io.github.nasserkhosravi.calendar.base

import java.util.Date


interface DateHelper {

    class CustomDate(val year: Int, val month: Int, val day: Int)

    fun getMinYear(): Int

    fun getMaxYear(): Int

    fun getMinDay(year: Int, month: Int): Int

    fun getMaxDay(year: Int, month: Int, day: Int): Int

    fun getMinMonth(year: Int): Int

    fun getMaxMonth(year: Int): Int

    fun formatToCustomDate(selectedDate: String): CustomDate

    fun formatToIsoDate(year: Int, month: Int, day: Int): String

    fun getStartDate(): Date

    fun getEndDate(): Date

    fun isValidYear(year: Int): Boolean {
        if (year in getMinYear()..getMaxYear())
            return true
        return false
    }
}