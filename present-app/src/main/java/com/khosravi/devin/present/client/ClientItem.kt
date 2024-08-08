package com.khosravi.devin.present.client

import android.view.LayoutInflater
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemClientBinding
import com.khosravi.devin.present.tool.adapter.FastBindingItem

class ClientItem(
    val data: ClientData,
) : FastBindingItem<ItemClientBinding>() {

    override val type: Int = R.id.vh_item_client

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemClientBinding {
        return ItemClientBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemClientBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.apply {
            view.text = data.packageId
        }
    }

}