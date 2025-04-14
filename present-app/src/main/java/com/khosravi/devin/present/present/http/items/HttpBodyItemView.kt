package com.khosravi.devin.present.present.http.items

import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemHttpBodyRowBinding
import com.khosravi.devin.present.tool.adapter.FastBindingItem

class HttpBodyItemView(
    var line: SpannableStringBuilder
) : FastBindingItem<ItemHttpBodyRowBinding>() {

    override val type: Int
        get() = R.id.vh_item_http_body_row

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemHttpBodyRowBinding {
        return ItemHttpBodyRowBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemHttpBodyRowBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.tvText.text = line
    }



}