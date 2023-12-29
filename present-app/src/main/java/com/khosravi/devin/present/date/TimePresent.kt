package com.khosravi.devin.present.date

import com.khosravi.devin.present.withPadding

data class TimePresent(
    val timestamp: Long,
) : TemporalPresent {
    var dumbed: DumbTime? = null

    override fun getFormatted(): String {
        return Companion.getFormatted(dumbed!!)
    }

    companion object {

        fun getFormatted(value: DumbTime): String {
            return "${value.hour.withPadding()}$SEPARATOR${value.minute.withPadding()}$SEPARATOR${value.second.withPadding()}"
        }

        private const val SEPARATOR = ":"
    }

}