package com.khosravi.devin.present.present.http.items

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemHttpCopyActionsRowBinding
import com.khosravi.devin.present.setClipboardSafe
import com.khosravi.devin.present.tool.adapter.FastBindingItem

class HttpCopyActionsItemView(
    private val formattedBodyProvider: () -> String,
    private val onCopyCurlClicked: () -> Unit,
) : FastBindingItem<ItemHttpCopyActionsRowBinding>() {

    override val type: Int
        get() = R.id.vh_item_http_copy_actions_row

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemHttpCopyActionsRowBinding {
        return ItemHttpCopyActionsRowBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemHttpCopyActionsRowBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        binding.rowCopyBody.setOnClickListener {
            val context = binding.root.context
            if (context.setClipboardSafe(formattedBodyProvider())) {
                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
            }
        }
        binding.rowCopyCurl.setOnClickListener {
            onCopyCurlClicked()
        }
    }
}
