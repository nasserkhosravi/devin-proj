package io.github.nasserkhosravi.calendar.gregorian

import io.github.nasserkhosravi.calendar.base.MonthProvider

class RegularMonthProvider : MonthProvider {
    override val monthNames = arrayOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    )


}