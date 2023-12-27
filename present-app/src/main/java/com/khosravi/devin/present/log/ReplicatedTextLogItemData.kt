package com.khosravi.devin.present.log

import com.khosravi.devin.present.date.TimePresent

class ReplicatedTextLogItemData(
    val list: List<TextLogItemData>
) : LogItemData {

    val text: String
        get() = list.first().text

    val timePresent: TimePresent
        get() = list.first().timePresent

}