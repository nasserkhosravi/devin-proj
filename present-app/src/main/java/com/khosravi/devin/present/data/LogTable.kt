package com.khosravi.devin.present.data

class LogTable(
    val id: Long,
    val tag: String,
    val value: String,
    val date: Long,
    val meta: String?
) {

    companion object {
        const val TABLE_NAME = "log"
    }
}