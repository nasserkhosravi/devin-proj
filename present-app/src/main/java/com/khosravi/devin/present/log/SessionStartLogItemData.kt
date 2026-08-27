package com.khosravi.devin.present.log

import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.TimePresent

class SessionStartLogItemData(
    val appVersionName: String?,
    val datePresent: DatePresent,
    val timePresent: TimePresent,
) : LogItemData
