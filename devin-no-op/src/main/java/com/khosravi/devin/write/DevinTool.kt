package com.khosravi.devin.write

import android.content.Context

class DevinTool private constructor(
    val logger: DevinLogger?
) {

    companion object {

        fun create(appContext: Context) = DevinTool(null)

    }
}