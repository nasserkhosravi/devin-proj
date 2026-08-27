package com.khosravi.devin.present.present.http.items

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemHttpCopyBodyRowBinding
import com.khosravi.devin.present.setClipboardSafe
import com.khosravi.devin.present.tool.adapter.FastBindingItem

class HttpCopyBodyItemView(
    private val formattedBodyProvider: () -> String,
    private val onCopyCurlClicked: () -> Unit,
) : FastBindingItem<ItemHttpCopyBodyRowBinding>() {

    override val type: Int
        get() = R.id.vh_item_http_copy_body_row

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemHttpCopyBodyRowBinding {
        return ItemHttpCopyBodyRowBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemHttpCopyBodyRowBinding, payloads: List<Any>) {
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
