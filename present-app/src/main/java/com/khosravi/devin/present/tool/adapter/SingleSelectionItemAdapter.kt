package com.khosravi.devin.present.tool.adapter

import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.adapters.ModelAdapter

//todo: why the class accept generic item?
class SingleSelectionItemAdapter<Item : GenericItem> : ItemAdapter<Item>() {
    var selectedIndex = -1

    fun checkSelection() {
        if (hasSelected()) {
            val oldItem = getAdapterItem(selectedIndex)
            if (oldItem is SelectableBindingItem<*>) {
                oldItem.setStateTo(true)
            }
            set(selectedIndex, oldItem)
        }
    }

    fun changeState(newIndex: Int) {
        if (selectedIndex != newIndex) {
            val oldIndex = selectedIndex
            selectedIndex = newIndex
            reverse(newIndex)

            if (oldIndex > -1) {
                val oldItem = getAdapterItem(oldIndex)
                if (oldItem is SelectableBindingItem<*>) {
                    oldItem.reverseState()
                }
                set(oldIndex, oldItem)
            }
        }
    }

    fun reverse(newIndex: Int) {
        val newItem = getAdapterItem(newIndex)
        if (newItem is SelectableBindingItem<*>) {
            newItem.reverseState()
        }
        set(newIndex, newItem)
    }

    fun reset() {
        if (hasSelected()) {
            if (selectedIndex > adapterItemCount - 1) {

            } else {
                val oldItem = getAdapterItem(selectedIndex)
                if (oldItem is SelectableBindingItem<*>) {
                    oldItem.reverseState()
                }
                set(selectedIndex, oldItem)
            }
        }
        selectedIndex = -1
    }

    override fun clear(): ModelAdapter<Item, Item> {
        selectedIndex = -1
        return super.clear()
    }

    fun hasSelected() = selectedIndex > -1

    fun isSelected(index: Int) = index == selectedIndex

    fun getSelectedItem(): Item {
        val selectedItem = optSelectedItem()
        if (selectedItem != null) {
            return selectedItem
        }
        throw IllegalStateException("no item is selected")
    }

    fun optSelectedItem(): Item? {
        if (selectedIndex != -1) {
            return itemList[selectedIndex]
        }
        return null
    }
}