package com.khosravi.devin.present.present.http

import com.google.gson.Gson
import com.google.gson.GsonBuilder

internal object GsonConverter {
    private val nonNullSerializerInstance: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create()
    }

    val instance: Gson by lazy {
        nonNullSerializerInstance.newBuilder()
            .serializeNulls()
            .create()
    }
}
