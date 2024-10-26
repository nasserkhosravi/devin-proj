package com.khosravi.devin.write

import io.nasser.devin.api.DevinCustomValue

class DevinCustomValueImpl(
    private val isEnable: Boolean
) : DevinCustomValue {

    //TODO: should read from content provider
    private val hashMap = hashMapOf<String, String>()

    override fun getOrNull(key: String, receiver: (value: String?) -> Unit) {
        if (isEnable) {
            val value = hashMap[key]
            receiver.invoke(value)
        }
    }

    override fun getOrDefault(key: String, default: String, receiver: (value: String) -> Unit) {
        if (isEnable) {
            val value = hashMap[key] ?: default
            receiver.invoke(value)
        }
    }

}