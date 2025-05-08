package com.khosravi.devin.present.date

data class DumbTime(
    override val hour: Int,
    override val minute: Int,
    override val second: Int,
) : IDumbTime