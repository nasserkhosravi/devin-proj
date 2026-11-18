package com.khosravi.devin.present.log

import com.khosravi.devin.present.date.TimePresent

/** Back-to-back text logs with the same tag and message, newest first. */
class ReplicatedTextLogItemData(
    val list: List<TextLogItemData>
) : LogItemData {

    val text: String
        get() = list.first().text

    val tag: String
        get() = list.first().tag

    /** The most severe level among the copies (android `Log` constants grow with severity). */
    val logLevel: Int
        get() = list.maxOf { it.logLevel }

    val count: Int
        get() = list.size

    /** The newest copy. */
    val timePresent: TimePresent
        get() = list.first().timePresent

    /** Stable across pages while the newest copy stays the same; used to remember which groups are expanded. */
    val groupKey: String
        get() = "$tag|$text|${list.first().timePresent.timestamp}"

}
