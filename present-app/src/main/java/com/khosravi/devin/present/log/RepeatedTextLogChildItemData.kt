package com.khosravi.devin.present.log

/** One copy inside an expanded [ReplicatedTextLogItemData], shown right below its group header. */
class RepeatedTextLogChildItemData(
    val groupKey: String,
    val log: TextLogItemData,
) : LogItemData
