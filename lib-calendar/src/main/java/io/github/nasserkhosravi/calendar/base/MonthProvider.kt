package io.github.nasserkhosravi.calendar.base

interface MonthProvider {
    val monthNames: Array<String>

    fun getMonthName(index: Int): String = monthNames[index]
}