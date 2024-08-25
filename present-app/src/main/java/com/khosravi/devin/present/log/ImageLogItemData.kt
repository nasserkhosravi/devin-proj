package com.khosravi.devin.present.log

import com.khosravi.devin.present.data.ImageLogData
import com.khosravi.devin.present.date.DatePresent
import com.khosravi.devin.present.date.TimePresent
import java.io.Serializable

class ImageLogItemData(
    val data: ImageLogData,
    val datePresent: DatePresent,
    val timePresent: TimePresent
) : LogItemData, Serializable
