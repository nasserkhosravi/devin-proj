package com.khosravi.devin.present.log

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.R
import com.khosravi.devin.present.data.http.HttpLogOperationStatus
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

        val statusColor : Int
        val statusText: String
        val operationStatus = data.data.operationStatus
        when (operationStatus) {
            is HttpLogOperationStatus.Respond -> {
                val statusCode = operationStatus.status
                statusText = statusCode.toString()
                statusColor = if (statusCode in 400..600) R.color.status_error else R.color.text_primary
            }

            HttpLogOperationStatus.Requested -> {
                statusText = "Requested"
                statusColor = R.color.text_primary
            }

            HttpLogOperationStatus.NetworkFailed -> {
                statusText = "!!!"
                statusColor = R.color.status_error
            }

            HttpLogOperationStatus.Unsupported -> {
                statusText = ""
                statusColor = R.color.text_primary
            }
        }
        binding.run {
            tvStatus.text = statusText
            val color = ContextCompat.getColor(context, statusColor)
            tvStatus.setTextColor(color)
            tvPath.setTextColor(color)
            tvPath.text = data.getL1SummeryText()
            tvDomain.text = data.getFullDomainText()
            tvTime.text = data.getTimeText(calender)
        }
    }

}