package com.khosravi.devin.present.filter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemFilterBinding
import com.khosravi.devin.present.tool.adapter.SelectableBindingItem
import com.khosravi.devin.write.okhttp.read.DevinHttpFlagsApi

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

            if (data is TagFilterItem && data.tagValue == DevinHttpFlagsApi.LOG_TAG) {
                styleAsOkHttpChip()
            } else {
                styleAsDefaultChip()
            }
        }
    }

    private fun ItemFilterBinding.styleAsOkHttpChip() {
        chip.text = "http"
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        chip.chipBackgroundColor = ColorStateList(
            states,
            intArrayOf(
                ContextCompat.getColor(chip.context, R.color.tag_okhttp_container_selected),
                ContextCompat.getColor(chip.context, R.color.tag_okhttp_container_unselected)
            )
        )
        chip.setTextColor(
            ColorStateList(
                states,
                intArrayOf(
                    ContextCompat.getColor(chip.context, R.color.tag_okhttp_text_selected),
                    ContextCompat.getColor(chip.context, R.color.tag_okhttp_text_unselected)
                )
            )
        )
    }

    private fun ItemFilterBinding.styleAsDefaultChip() {
        chip.chipBackgroundColor = ContextCompat.getColorStateList(chip.context, R.color.chip_colors)
        chip.setTextColor(ContextCompat.getColorStateList(chip.context, R.color.chip_text_color))
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