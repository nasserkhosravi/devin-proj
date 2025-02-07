package com.khosravi.devin.present.filter

import com.khosravi.devin.present.data.LogData

interface FilterCriteria {
    fun applyCriteria(logs: List<LogData>): List<LogData>
}