package com.khosravi.devin.write

import android.content.Context
import com.khosravi.devin.api.DevinImageLogger
import com.khosravi.devin.api.DevinLogger
import com.khosravi.devin.write.api.DevinLogCore
import org.json.JSONObject

class DevinTool private constructor(
    val logger: DevinLogger?,
    val imageLogger: DevinImageLogger?,
    private val logCore: DevinLogCore? = null,
) {

    /**
     * Give available [DevinLogCore] instance.
     */
    fun connectPlugin(action: (logCore: DevinLogCore) -> Unit) {
        //no impl
    }

    companion object {


        fun get(): DevinTool? = null

        fun init(context: Context) {
            //no impl
        }

        fun init(context: Context, isEnable: Boolean? = null, presenterConfig: JSONObject? = null) {
            //no impl
        }

    }
}