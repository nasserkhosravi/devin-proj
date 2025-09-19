package com.khosravi.devin.present.client

fun ClientData.getLogPassword(): String? {
    val string = presenterConfig?.getString("logPassword")
    if (!string.isNullOrEmpty()){
        return string
    }
    return null
}