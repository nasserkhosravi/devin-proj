package com.khosravi.devin.present.data

import com.khosravi.devin.write.room.LogTable

class LogData(
    val id: Long,
    val tag: String,
    val value: String,
    val date: Long,
    val meta: String?,
    val packageId: String,
) {

    companion object {
        const val KEY_TAG = "tag"
        const val KEY_MESSAGE = "message"
        const val KEY_DATE = "date"
        const val KEY_META = "meta"
        const val KEY_CLIENT_ID = LogTable.COLUMN_CLIENT_ID
    }
}