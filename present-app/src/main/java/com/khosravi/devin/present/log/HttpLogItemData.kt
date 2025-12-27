package com.khosravi.devin.present.log

import com.khosravi.devin.present.data.http.HttpLogData
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.TimePresent

class HttpLogItemData(
    val data: HttpLogData
) : LogItemData {

    val logId = data.logId

    private val time by lazy { TimePresent(data.date) }

    fun getL1SummeryText(): String = "${data.httpMethod} ${data.urlQuery.path}"

    fun getFullDomainText(): String = "${data.urlQuery.protocol}://${data.urlQuery.domain}"

    fun getTimeText(calender: CalendarProxy): String {
        return calender.initIfNeed(time).getFormatted()
    }

}
