package com.khosravi.devin.present.present.itemview

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SearchView.OnQueryTextListener
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemSearchBinding
import com.khosravi.devin.present.tool.adapter.FastBindingItem

class SearchItemView(
    private var onSearchTextChange: ((text: String?) -> Unit)? = null,
    var searchText: String? = null,
    var searchHint: String? = null,
) : FastBindingItem<ItemSearchBinding>() {

    override val type: Int
        get() = R.id.vh_item_search

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemSearchBinding {
        return ItemSearchBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemSearchBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        val edMessage = binding.edMessage
        edMessage.queryHint = searchHint
        edMessage.setQuery(searchText, false)
        edMessage.setOnQueryTextListener(object : OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                onSearchTextChange?.invoke(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                onSearchTextChange?.invoke(newText)
                return true
            }
        })

    }

    override fun unbindView(binding: ItemSearchBinding) {
        super.unbindView(binding)
        binding.edMessage.setOnQueryTextListener(null)
    }

}