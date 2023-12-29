package com.khosravi.devin.present.date


/**
 * A [DatePresent] know how to present itself that means the class know how to format its [timestamp].
 */
data class DatePresent(
    val timestamp: Long,
    var dumbed: DumbDate? = null
) : TemporalPresent {

    override fun getFormatted(): String {
        return getFormatted(dumbed!!)
    }

    companion object {

        fun getFormatted(value: DumbDate) = "${value.year}$SEPARATOR${value.month}$SEPARATOR${value.day}"
        private const val SEPARATOR = "-"
    }
}