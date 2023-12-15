package com.khosravi.devin.present.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemFilterBinding
import com.khosravi.devin.present.tool.adapter.SelectableBindingItem

class FilterItemViewHolder(
    val data: FilterUiData,
) : SelectableBindingItem<ItemFilterBinding>() {

    override val type: Int = R.id.vh_item_filter

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemFilterBinding {
        return ItemFilterBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemFilterBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.apply {
            chip.text = data.title.value
        }
    }

    override fun onBindSelected(binding: ItemFilterBinding) {
        super.onBindSelected(binding)
        binding.chip.isSelected = true
    }

    override fun onBindNotSelected(binding: ItemFilterBinding) {
        super.onBindNotSelected(binding)
        binding.chip.isSelected = false
    }

}