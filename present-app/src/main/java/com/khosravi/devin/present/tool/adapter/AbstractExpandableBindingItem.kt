package com.khosravi.devin.present.tool.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.viewbinding.ViewBinding
import com.mikepenz.fastadapter.binding.BindingViewHolder
import com.mikepenz.fastadapter.expandable.items.AbstractExpandableItem

abstract class AbstractExpandableBindingItem<Binding : ViewBinding> : AbstractExpandableItem<BindingViewHolder<Binding>>() {

    override fun getViewHolder(parent: ViewGroup): BindingViewHolder<Binding> {
        val binding = createBinding(LayoutInflater.from(parent.context), parent)
        return BindingViewHolder(binding)
    }

    override fun getViewHolder(v: View): BindingViewHolder<Binding> {
        return getViewHolder(v.parent as ViewGroup)
    }

    abstract fun createBinding(inflater: LayoutInflater, parent: ViewGroup? = null): Binding

    @CallSuper
    override fun bindView(holder: BindingViewHolder<Binding>, payloads: List<Any>) {
        super.bindView(holder, payloads)
        bindView(holder.binding, payloads)
    }

    open fun bindView(binding: Binding, payloads: List<Any>) {}

    override fun unbindView(holder: BindingViewHolder<Binding>) {
        super.unbindView(holder)
        unbindView(holder.binding)
    }

    open fun unbindView(binding: Binding) {}

    override fun attachToWindow(holder: BindingViewHolder<Binding>) {
        super.attachToWindow(holder)
        attachToWindow(holder.binding)
    }

    open fun attachToWindow(binding: Binding) {}

    override fun detachFromWindow(holder: BindingViewHolder<Binding>) {
        super.detachFromWindow(holder)
        detachFromWindow(holder.binding)
    }

    open fun detachFromWindow(binding: Binding) {}


}