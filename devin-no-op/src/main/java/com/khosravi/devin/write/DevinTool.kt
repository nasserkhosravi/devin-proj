package com.khosravi.devin.write

import android.content.Context
import com.khosravi.devin.api.DevinImageLogger
import com.khosravi.devin.api.DevinLogger

class DevinTool private constructor(
    val logger: DevinLogger?,
    val imageLogger: DevinImageLogger?
) {

    companion object {

        fun create(appContext: Context) = DevinTool(null, null)
        fun getOrCreate(context: Context): DevinTool? = null
        fun get(): DevinTool? = null
    }
}