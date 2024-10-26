package io.nasser.devin.api


interface DevinCustomValue {

    fun getOrNull(key: String, receiver: (value: String?) -> Unit)

    fun getOrDefault(key: String, default: String, receiver: (value: String) -> Unit)
}