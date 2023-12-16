package com.khosravi.devin.present.log

import android.view.LayoutInflater
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemHeaderLogDateBinding
import com.khosravi.devin.present.date.CalenderProxy
import com.khosravi.devin.present.tool.adapter.FastBindingItem

class HeaderLogDateItem(
    private val calender: CalenderProxy,
    private val date: DateLogItemData
) : FastBindingItem<ItemHeaderLogDateBinding>() {

    override val type: Int = R.id.vh_item_header_log_date

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemHeaderLogDateBinding {
        return ItemHeaderLogDateBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemHeaderLogDateBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.chipText.text = calender.initIfNeed(date.presentDate).getFormatted()
    }

}