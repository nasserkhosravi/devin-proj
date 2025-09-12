package com.khosravi.devin.write.bridge

import android.content.Context
import android.net.Uri
import com.khosravi.devin.write.room.LogTable
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberFunctions

class BridgeImpl() {

    companion object {
        private const val CLASSNAME = "com.khosravi.devin.present.data.ContentProviderBridge"
        private const val FUNC_ON_INSERT_LOG = "onInsertLog"
    }

    private var funcOnInsertLog: KFunction<*>? = null
    private var objectInstance: Any

    init {
        val jClass = Class.forName(CLASSNAME)
        val kClass = jClass.kotlin
        objectInstance = kClass.objectInstance!!

        funcOnInsertLog = kClass.memberFunctions.find { it.name == FUNC_ON_INSERT_LOG }
    }

    fun signalOnInsertLog(logUri: Uri, context: Context, logTable: LogTable) {
        funcOnInsertLog?.call(objectInstance, logUri, context, logTable)
    }
}