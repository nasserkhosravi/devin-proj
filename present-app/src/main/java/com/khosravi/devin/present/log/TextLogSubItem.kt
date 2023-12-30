package com.khosravi.devin.present.log

import com.khosravi.devin.present.databinding.ItemLogBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.mikepenz.fastadapter.IParentItem
import com.mikepenz.fastadapter.ISubItem
import com.mikepenz.fastadapter.binding.BindingViewHolder

class TextLogSubItem(
    calender: CalendarProxy,
    data: TextLogItemData,
    override var parent: IParentItem<*>?,
) : TextLogItem(calender,data), ISubItem<BindingViewHolder<ItemLogBinding>>