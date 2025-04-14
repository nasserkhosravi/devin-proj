package com.khosravi.devin.present.present.http.items

import android.text.Spanned
import android.view.LayoutInflater
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemHttpHeaderRowBinding
import com.khosravi.devin.present.tool.adapter.FastBindingItem

class HttpHeaderItemView(
    val value: Spanned,
) : FastBindingItem<ItemHttpHeaderRowBinding>() {

    override val type: Int
        get() = R.id.vh_item_http_header_row

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemHttpHeaderRowBinding {
        return ItemHttpHeaderRowBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemHttpHeaderRowBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.tvHeaders.text = value
    }
}