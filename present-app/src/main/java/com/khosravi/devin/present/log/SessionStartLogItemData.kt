package com.khosravi.devin.present.log

import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.TimePresent

class SessionStartLogItemData(
    val appVersionName: String?,
    val datePresent: DatePresent,
    val timePresent: TimePresent,
    /** The stored log, shown in the log detail screen when the row is clicked. */
    val detail: TextLogItemData,
) : LogItemData
