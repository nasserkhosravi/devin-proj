package com.khosravi.devin.write.bridge

import android.content.Context
import android.net.Uri
import androidx.annotation.experimental.Experimental
import com.khosravi.devin.write.room.LogTable

//Its a experimental API.
class BridgeSignaler {

    private val any = Any()
    private var instance: BridgeImpl? = null

    fun signalOnInsertLog(logUri: Uri, context: Context, logTable: LogTable) {
        synchronized(any) {
            if (instance == null) {
                instance = BridgeImpl()
            }
            instance?.signalOnInsertLog(logUri, context, logTable)
        }
    }
}



