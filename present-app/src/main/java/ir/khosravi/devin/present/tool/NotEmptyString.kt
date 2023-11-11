package ir.khosravi.devin.present.tool

import java.lang.IllegalArgumentException

@JvmInline
value class NotEmptyString(val value: String) {

    init {
        if (value.isEmpty()) {
            throw IllegalArgumentException()
        }
    }

}