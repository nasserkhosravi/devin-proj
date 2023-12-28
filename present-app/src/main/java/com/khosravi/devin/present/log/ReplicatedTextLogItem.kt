package com.khosravi.devin.present.log

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemReplicatedTextLogBinding
import com.khosravi.devin.present.date.CalenderProxy
import com.khosravi.devin.present.getLogColor
import com.khosravi.devin.present.tool.adapter.AbstractExpandableBindingItem
import com.mikepenz.fastadapter.ClickListener
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.IClickable

class ReplicatedTextLogItem(
    private val calender: CalenderProxy,
    val data: ReplicatedTextLogItemData,
) : AbstractExpandableBindingItem<ItemReplicatedTextLogBinding>(), IClickable<ReplicatedTextLogItem> {

    override val type: Int = R.id.vh_item_replicated_text_log

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemReplicatedTextLogBinding {
        return ItemReplicatedTextLogBinding.inflate(inflater, parent, false)
    }

    override val layoutRes: Int
        get() = R.layout.item_replicated_text_log

    override fun bindView(binding: ItemReplicatedTextLogBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        val dateText = calender.initIfNeed(data.timePresent).getFormatted()
        binding.run {
            tvDate.text = dateText
            ctvCount.text = data.list.size.toString()
            tvText.text = data.text
            tvText.setTextColor(data.list.first().getLogColor(context))
        }
    }

    override var onPreItemClickListener: ClickListener<ReplicatedTextLogItem>? = null

    @Suppress("SetterBackingFieldAssignment")
    override var onItemClickListener: ((v: View?, adapter: IAdapter<ReplicatedTextLogItem>, item: ReplicatedTextLogItem, position: Int) -> Boolean)? = null

}