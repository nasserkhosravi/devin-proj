package com.khosravi.devin.present.date

data class DumbDate(
    override val year: Int,
    override val month: Int,
    override val day: Int,
) : IDumbDate