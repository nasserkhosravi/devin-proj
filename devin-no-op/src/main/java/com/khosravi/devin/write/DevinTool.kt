package com.khosravi.devin.write

import android.content.Context
import io.nasser.devin.api.DevinLogger

class DevinTool private constructor(
    val logger: DevinLogger?
) {

    companion object {

        fun create(appContext: Context) = DevinTool(null)

    }
}