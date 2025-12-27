package com.khosravi.devin.present.filter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemFilterBinding
import com.khosravi.devin.present.tool.adapter.SelectableBindingItem

class FilterItemViewHolder(
    val data: FilterItem,
) : SelectableBindingItem<ItemFilterBinding>() {

    override val type: Int = R.id.vh_item_filter

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemFilterBinding {
        return ItemFilterBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemFilterBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.apply {
            val ui = data.ui
            chip.text = ui.title.value
            if (ui.isPinned) {
                chip.isChipIconVisible = true
                chip.setChipIconResource(R.drawable.ic_keep_24px)
                chip.setChipIconTintResource(R.color.chip_text_color)
            } else {
                chip.isChipIconVisible = false
                chip.chipIcon = null
            }
        }
    }

    override fun onBindSelected(binding: ItemFilterBinding) {
        super.onBindSelected(binding)
        binding.run {
            chip.isSelected = true
            chip.isChecked = true
        }
    }

    override fun onBindNotSelected(binding: ItemFilterBinding) {
        super.onBindNotSelected(binding)
        binding.run {
            chip.isSelected = false
            chip.isChecked = false
        }
    }

}