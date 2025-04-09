package com.khosravi.devin.present.log

import android.graphics.Color
import com.khosravi.devin.present.data.http.HttpLogData
import com.khosravi.devin.present.data.http.HttpLogOperationStatus
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.TimePresent

class HttpLogItemData(
    private val data: HttpLogData
) : LogItemData {

    val logId = data.logId

    private val time by lazy { TimePresent(data.date) }

    fun getL1SummeryText(): String = "${data.httpMethod} ${data.urlQuery.path}"

    fun getFullDomainText(): String = "${data.urlQuery.protocol}://${data.urlQuery.domain}"

    fun getStatusTextAndColor(): Pair<String, Int> {
        val statusText: String
        val statusColor: Int

        when (data.operationStatus) {
            is HttpLogOperationStatus.Respond -> {
                val statusCode = data.operationStatus.status
                statusText = statusCode.toString()
                statusColor = if (statusCode in 400..600) Color.RED else Color.BLACK
            }

            HttpLogOperationStatus.Requested -> {
                statusText = "Requested"
                statusColor = Color.GRAY
            }

            HttpLogOperationStatus.NetworkFailed -> {
                statusText = "!!!"
                statusColor = Color.RED
            }

            HttpLogOperationStatus.Unsupported -> {
                statusText = ""
                statusColor = Color.BLACK
            }
        }

        return statusText to statusColor
    }

    fun getTimeText(calender: CalendarProxy): String {
        return calender.initIfNeed(time).getFormatted()
    }

}
