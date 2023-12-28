package com.khosravi.devin.present.date

import com.khosravi.devin.present.withPadding

data class TimePresent(
    val timestamp: Long,
) : TemporalPresent {
    var dumbed: DumbTime? = null

    override fun getFormatted(): String {
        return dumbed!!.let {
            "${it.hour.withPadding()}$SEPARATOR${it.minute.withPadding()}$SEPARATOR${it.second.withPadding()}"
        }
    }

    companion object {
        private const val SEPARATOR = ":"
    }

}