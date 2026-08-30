package com.khosravi.devin.present.log

sealed interface LogListRowItem {
    data class Search(val sessionId: Int, val filterId: String, val hint: String, val text: String?) : LogListRowItem
    data class Row(val data: LogItemData) : LogListRowItem
}
