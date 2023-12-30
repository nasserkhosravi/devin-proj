package  com.khosravi.devin.present.tool.adapter

import androidx.viewbinding.ViewBinding

abstract class SelectableBindingItem<VB : ViewBinding>() : FastBindingItem<VB>() {

    fun reverseState() {
        isSelected = !isSelected
    }

    fun setStateTo(boolean: Boolean) {
        isSelected = boolean
    }

    override fun bindView(binding: VB, payloads: List<Any>) {
        super.bindView(binding, payloads)
        if (isSelected) {
            onBindSelected(binding)
        } else {
            onBindNotSelected(binding)
        }
    }

    open fun onBindSelected(binding: VB) {}

    open fun onBindNotSelected(binding: VB) {}

}

