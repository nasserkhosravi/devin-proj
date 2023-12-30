package com.khosravi.devin.present.tool

import java.lang.ref.WeakReference

class TempReference<T : Any> {

    private var reference: WeakReference<T>? = null

    fun get(creator: () -> T): T {
        val instance = reference?.get()
        if (instance == null) {
            val freshInstance = creator()
            reference = WeakReference(freshInstance)
            return freshInstance
        }
        return instance
    }

    fun isInitialized() = reference?.get() != null

    fun clear() {
        reference?.clear()
        reference = null
    }
}