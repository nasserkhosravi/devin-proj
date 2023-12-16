package com.khosravi.devin.present.date


/**
 * A [DatePresent] know how to present itself that means the class know how to format its [timestamp].
 */
data class DatePresent(
    val timestamp: Long,
    var dumbed: DumbDate? = null
) : TemporalPresent {

    override fun getFormatted(): String {
        return dumbed!!.let {
            "${it.year}$SEPARATOR${it.month}$SEPARATOR${it.day}"
        }
    }

    companion object {
        private const val SEPARATOR = "-"
    }
}