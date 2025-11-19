package com.khosravi.devin.present.client

import com.khosravi.devin.present.optStringOrNull

fun ClientData.getLogPassword(): String? {
    val string = presenterConfig?.optStringOrNull("logPassword")
    if (!string.isNullOrEmpty()){
        return string
    }
    return null
}