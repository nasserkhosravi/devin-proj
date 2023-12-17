package com.khosravi.devin.present.date

sealed class CalendarType {
    data object PERSIAN : CalendarType()
    data object GREGORIAN : CalendarType()
}