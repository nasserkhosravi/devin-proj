package com.khosravi.devin.present.date

data class TimePresent(
    val timestamp: Long,
) : TemporalPresent {
    var dumbed: DumbTime? = null

    override fun getFormatted(): String {
        return dumbed!!.let {
            "${it.hour}$SEPARATOR${it.minute}$SEPARATOR${it.second}"
        }
    }

    companion object {
        private const val SEPARATOR = ":"
    }

}