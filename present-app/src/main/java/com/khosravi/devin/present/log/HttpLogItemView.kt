package com.khosravi.devin.present.log

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemHttpBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.tool.adapter.FastBindingItem

open class HttpLogItemView(
    private val calender: CalendarProxy,
    val data: HttpLogItemData,
) : FastBindingItem<ItemHttpBinding>() {

    override val type: Int = R.id.vh_item_http_log

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemHttpBinding {
        return ItemHttpBinding.inflate(inflater, parent, false).apply {
            tvDomain.setTextColor(Color.GRAY)
        }
    }

    override fun bindView(binding: ItemHttpBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)

        val (statusText: String, statusColor: Int) = data.getStatusTextAndColor()
        binding.run {
            tvStatus.text = statusText
            tvStatus.setTextColor(statusColor)
            tvPath.setTextColor(statusColor)
            tvPath.text = data.getL1SummeryText()
            tvDomain.text = data.getFullDomainText()
            tvTime.text = data.getTimeText(calender)
        }
    }

}