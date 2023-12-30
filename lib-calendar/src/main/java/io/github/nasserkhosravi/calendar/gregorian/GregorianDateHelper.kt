package io.github.nasserkhosravi.calendar.gregorian

import io.github.nasserkhosravi.calendar.base.DateHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

class GregorianDateHelper(
    private val dateFormatter: SimpleDateFormat,
    startDate: String,
    endDate: String
) : DateHelper {
    private val calendar: Calendar = Calendar.getInstance()
    private val startDate: Date = dateFormatter.parse(startDate)!!
    private val endDate: Date = dateFormatter.parse(endDate)!!

    override fun getMinYear(): Int {
        calendar.time = startDate
        return calendar[Calendar.YEAR]
    }

    override fun getMaxYear(): Int {
        calendar.time = endDate
        return calendar[Calendar.YEAR]
    }

    override fun getMinDay(year: Int, month: Int): Int {
        if (!isValidYear(year))
            return -1
        calendar.time = startDate
        val startMonth = calendar[Calendar.MONTH]
        return if (year == getMinYear() && month == startMonth)
            calendar[Calendar.DAY_OF_MONTH]
        else
            1
    }

    override fun getMaxDay(year: Int, month: Int, day: Int): Int {
        if (!isValidYear(year))
            return -1
        calendar.time = endDate
        val endMonth = calendar[Calendar.MONTH]
        return if (year == getMaxYear() && month == endMonth) calendar[Calendar.DAY_OF_MONTH]
        else getMonthMaxDay(year, month, day)
    }

    private fun getMonthMaxDay(year: Int, month: Int, day: Int): Int {
        if (!isValidYear(year))
            return -1
        val mycal: Calendar = GregorianCalendar(year, month - 1, day)
        return mycal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    override fun getMinMonth(year: Int): Int {
        if (!isValidYear(year))
            return -1
        return if (year <= getMinYear()) {
            calendar.time = startDate
            calendar[Calendar.MONTH] + 1
        } else 1
    }

    override fun getMaxMonth(year: Int): Int {
        if (!isValidYear(year))
            return -1
        return if (year >= getMaxYear()) {
            calendar.time = endDate
            calendar[Calendar.MONTH] + 1
        } else 12
    }

    override fun formatToCustomDate(selectedDate: String): DateHelper.CustomDate {
        calendar.time = dateFormatter.parse(selectedDate)!!
        calendar.time = dateFormatter.parse(selectedDate)!!
        val selectedYear = calendar[Calendar.YEAR]
        val selectedMonth = calendar[Calendar.MONTH] + 1
        val selectedDay = calendar[Calendar.DAY_OF_MONTH]
        return DateHelper.CustomDate(selectedYear, selectedMonth, selectedDay)

    }

    override fun formatToIsoDate(year: Int, month: Int, day: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        return dateFormatter.format(cal.time)
    }

    override fun getStartDate(): Date = startDate

    override fun getEndDate(): Date = endDate
}