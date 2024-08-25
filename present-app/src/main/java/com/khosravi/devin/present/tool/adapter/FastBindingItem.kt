package com.khosravi.devin.present.tool.adapter

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.mikepenz.fastadapter.binding.AbstractBindingItem

abstract class FastBindingItem<VB : ViewBinding> : AbstractBindingItem<VB>() {

    val VB.context: Context
        get() = root.context

    fun VB.getColor(@ColorRes colorRes: Int) = ContextCompat.getColor(context, colorRes)

}