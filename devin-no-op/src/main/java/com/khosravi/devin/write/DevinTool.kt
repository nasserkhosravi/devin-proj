package com.khosravi.devin.write

import android.content.Context
import com.khosravi.devin.api.DevinImageLogger
import com.khosravi.devin.api.DevinLogger

class DevinTool private constructor(
    val logger: DevinLogger?,
    val imageLogger: DevinImageLogger?,
) {

    companion object {


        fun create(context: Context, isEnable: Boolean? = null): DevinTool = DevinTool(null, null)

        fun get(): DevinTool? = null

        fun getOrCreate(context: Context): DevinTool? = null
    }
}