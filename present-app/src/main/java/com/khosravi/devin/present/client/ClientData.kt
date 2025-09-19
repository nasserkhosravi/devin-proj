package com.khosravi.devin.present.client

import org.json.JSONObject

data class ClientData(
    val packageId: String,
    val presenterConfig: JSONObject?
){
    val id: String
        get() = packageId
}
