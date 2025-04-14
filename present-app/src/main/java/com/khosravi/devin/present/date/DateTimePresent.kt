package com.khosravi.devin.present.date

data class DateTimePresent(
    val timestamp: Long,
) : TemporalPresent {

    var dumbed: DumbDateTime? = null

    override fun getFormatted(): String {
        return DatePresent.getFormatted(dumbed!!.dumbDate) + "T" + TimePresent.getFormatted(dumbed!!.dumbTime)
    }

}