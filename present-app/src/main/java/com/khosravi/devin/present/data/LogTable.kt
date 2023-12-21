package com.khosravi.devin.present.data

class LogTable(
    val id: Long,
    val type: String,
    val value: String,
    val date: Long,
) {

    companion object {
        const val TABLE_NAME = "log"
    }
}