package com.khosravi.devin.present.log

import android.view.LayoutInflater
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemLogBinding
import com.khosravi.devin.present.date.CalenderProxy
import com.khosravi.devin.present.getLogColor
import com.khosravi.devin.present.tool.adapter.FastBindingItem

open class TextLogItem(
    private val calender: CalenderProxy,
    val data: TextLogItemData,
) : FastBindingItem<ItemLogBinding>() {

    override val type: Int = R.id.vh_item_text_log

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemLogBinding {
        return ItemLogBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemLogBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        val dateText = calender.initIfNeed(data.timePresent).getFormatted()
        binding.tvText.text = dateText.plus(" ${data.text}")
        binding.tvText.setTextColor(data.getLogColor(binding.context))
    }

}