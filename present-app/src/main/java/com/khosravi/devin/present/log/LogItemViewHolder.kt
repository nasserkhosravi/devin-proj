package com.khosravi.devin.present.log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemLogBinding
import com.khosravi.devin.present.withPadding
import com.mikepenz.fastadapter.binding.AbstractBindingItem
import java.util.Calendar
import java.util.Date

class LogItemViewHolder(
    val data: LogItemData,
) : AbstractBindingItem<ItemLogBinding>() {

    private fun getDateText(data: LogItemData): String {
        val calendar = Calendar.getInstance().apply {
            time = Date(data.dateTimeStamp)
        }
        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).withPadding()
        val day = calendar.get(Calendar.DAY_OF_MONTH).withPadding()

        val hour = calendar.get(Calendar.HOUR_OF_DAY).withPadding()
        val minute = calendar.get(Calendar.MINUTE).withPadding()
        val second = calendar.get(Calendar.SECOND).withPadding()

        val dataText = "$year/$month/$day $hour:$minute:$second"
        return dataText
    }

    override val type: Int = R.id.vh_item_log

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemLogBinding {
        return ItemLogBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemLogBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        val dataText = getDateText(data)
        binding.tvText.text = dataText.plus(" ${data.text}")
    }

}