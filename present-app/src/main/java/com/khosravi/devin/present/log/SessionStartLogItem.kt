package com.khosravi.devin.present.log

import android.view.LayoutInflater
import android.view.ViewGroup
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemSessionStartBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.gone
import com.khosravi.devin.present.tool.adapter.FastBindingItem
import com.khosravi.devin.present.visible

class SessionStartLogItem(
    private val calendar: CalendarProxy,
    private val data: SessionStartLogItemData,
) : FastBindingItem<ItemSessionStartBinding>() {

    override val type: Int = R.id.vh_item_session_start_log

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemSessionStartBinding {
        return ItemSessionStartBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemSessionStartBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)

        binding.dateText.text = calendar.initIfNeed(data.datePresent).getFormatted()
        binding.timeText.text = calendar.initIfNeed(data.timePresent).getFormatted()
        binding.versionText.run {
            val versionName = data.appVersionName
            if (versionName.isNullOrBlank()) {
                gone()
            } else {
                text = context.getString(R.string.session_version, versionName)
                visible()
            }
        }
    }
}
