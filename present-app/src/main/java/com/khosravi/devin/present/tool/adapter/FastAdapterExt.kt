package com.khosravi.devin.present.tool.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.binding.BindingViewHolder


fun ItemAdapter<*>.lastIndex() = adapterItemCount - 1

fun ItemAdapter<*>.isEmpty() = adapterItemCount == 0

fun ItemAdapter<*>.isNotEmpty() = adapterItemCount != 0

inline fun <reified T> ItemAdapter<GenericItem>.indexOfFirst(predicate: (T) -> Boolean): Int {
    return adapterItems.indexOfFirst {
        if (it is T) {
            predicate(it)
        } else false
    }
}

inline fun <reified T> ItemAdapter<GenericItem>.indexOfFirstOrNull(predicate: (T) -> Boolean): Int? {
    val result = indexOfFirst(predicate)
    if (result != -1) {
        return result
    }
    return null
}

inline fun <reified T> ItemAdapter<GenericItem>.firstOrNull(predicate: (T) -> Boolean): T? {
    return adapterItems.firstOrNull {
        if (it is T) {
            predicate(it)
        } else false
    } as T?
}

fun <T : GenericItem> ItemAdapter<T>.getAdapterItemById(id: Long): T {
    val position = getAdapterPosition(id)
    return getAdapterItem(position)
}

fun <T : GenericItem> simpleClickListener(callBack: (item: T, position: Int) -> Boolean): (View?, IAdapter<T>, T, Int) -> Boolean {
    return { _: View?, _: IAdapter<T>, item: T, position: Int ->
        callBack.invoke(item, position)
    }
}

fun <T : GenericItem> ItemAdapter<T>.hasItemById(id: Long): Boolean {
    val position = getAdapterPosition(id)
    return position > -1
}

fun <T : GenericItem> ItemAdapter<T>.getAdapterPositionOrNull(item: T): Int? {
    val position = getAdapterPosition(item)
    if (position > -1) {
        return position
    }
    return null
}

fun <T : GenericItem> ItemAdapter<T>.hasItemByType(type: Int): Boolean {
    val result = adapterItems.firstOrNull { it.type == type }
    return result != null
}

fun <T : GenericItem> ItemAdapter<T>.updateItem(item: T): Boolean {
    val position = adapterItems.indexOfFirst { it == item }
    if (position != -1) {
        set(position, item)
        return true
    }
    return false
}

fun <T : GenericItem> ItemAdapter<T>.updateItem(position: Int): Boolean {
    val item = adapterItems.getOrNull(position)
    if (item != null) {
        set(position, item)
        return true
    }
    return false
}

fun <T : GenericItem> ItemAdapter<T>.addOrSetById(item: T, positionToAdd: Int = -1) {
    if (hasItemById(item.identifier)) {
        updateItem(item)
    } else {
        addToPositionOrEnd(item, positionToAdd)
    }
}

fun <T : GenericItem> ItemAdapter<T>.addToPositionOrEnd(item: T, position: Int) {
    if (position < 0) {
        add(item)
    } else {
        add(position, item)
    }
}

fun <T : GenericItem> ItemAdapter<out GenericItem>.sizeOf(type: Class<T>): Int {
    var result = 0
    adapterItems.forEach {
        if (it::class.java == type) {
            result++
        }
    }
    return result
}

inline fun <reified VB : ViewBinding> RecyclerView.findViewBindingOfItem(adapterPosition: Int): VB? {
    val vh = findViewHolderForAdapterPosition(adapterPosition)
    if (vh is BindingViewHolder<*>) {
        val itemViewBinding = vh.binding
        if (itemViewBinding is VB) {
            return itemViewBinding
        }
    }
    return null
}