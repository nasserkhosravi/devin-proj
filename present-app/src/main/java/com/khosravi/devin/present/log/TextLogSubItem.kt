package com.khosravi.devin.present.log

import com.khosravi.devin.present.databinding.ItemLogBinding
import com.khosravi.devin.present.date.CalenderProxy
import com.mikepenz.fastadapter.IParentItem
import com.mikepenz.fastadapter.ISubItem
import com.mikepenz.fastadapter.binding.BindingViewHolder

class TextLogSubItem(
    calender: CalenderProxy,
    data: TextLogItemData,
    override var parent: IParentItem<*>?,
) : TextLogItem(calender,data), ISubItem<BindingViewHolder<ItemLogBinding>>