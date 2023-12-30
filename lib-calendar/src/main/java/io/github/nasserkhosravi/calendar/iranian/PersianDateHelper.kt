package io.github.nasserkhosravi.calendar.iranian

import io.github.nasserkhosravi.calendar.base.DateHelper
import java.text.SimpleDateFormat
import java.util.*

class PersianDateHelper(
    private val dateFormatter: SimpleDateFormat,
    startDate: String,
    endDate: String
) : DateHelper {
    private val persianCal = PersianDate()
    private val startDate: PersianDate =
        PersianDate(dateFormatter.parse(startDate)!!.time)
    private val endDate: PersianDate =
        PersianDate(dateFormatter.parse(endDate)!!.time)

    override fun getMinYear(): Int {
        return startDate.shYear
    }

    override fun getMaxYear(): Int {
        return endDate.shYear
    }

    override fun getMinDay(year: Int, month: Int): Int {
        if (!isValidYear(year))
            return -1
        val startMonth = startDate.shMonth
        return if (year == getMinYear() && month == startMonth)
            startDate.shDay
        else
            1
    }

    override fun getMaxDay(year: Int, month: Int, day: Int): Int {
        if (!isValidYear(year))
            return -1
        val endMonth = endDate.shMonth
        return if (year == getMaxYear() && month == endMonth) endDate.shDay
        else getMonthMaxDay(year, month)
    }

    private fun getMonthMaxDay(year: Int, month: Int): Int {
        if (!isValidYear(year))
            return -1
        val isLeapYear = persianCal.isLeap(year)
        return when {
            month < 7 -> 31
            month < 12 -> 30
            month == 12 -> if (isLeapYear) 30 else 29
            else -> throw IllegalArgumentException("month is not valid")
        }
    }

    override fun getMinMonth(year: Int): Int {
        if (!isValidYear(year))
            return -1
        return if (year <= getMinYear()) {
            startDate.shMonth
        } else 1
    }

    override fun getMaxMonth(year: Int): Int {
        if (!isValidYear(year))
            return -1
        return if (year >= getMaxYear()) {
            endDate.shMonth
        } else 12
    }

    override fun formatToCustomDate(selectedDate: String): DateHelper.CustomDate {
        val calendar = PersianDate(dateFormatter.parse(selectedDate)!!.time)
        val selectedYear = calendar.shYear
        val selectedMonth = calendar.shMonth
        val selectedDay = calendar.shDay
        return DateHelper.CustomDate(selectedYear, selectedMonth, selectedDay)
    }

    override fun formatToIsoDate(year: Int, month: Int, day: Int): String {
        val persianCal = PersianDate()
        persianCal.shYear = year
        persianCal.shMonth = month
        persianCal.shDay = day
        return dateFormatter.format(persianCal.time)
    }

    override fun getStartDate(): Date = startDate.toDate()

    override fun getEndDate(): Date = endDate.toDate()
}