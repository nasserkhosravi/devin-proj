package com.khosravi.devin.present.tool

@JvmInline
value class PositiveNumber(val value: Int) {

    init {
        require(value > -1)
    }

}