package ir.khosravi.devin.present.filter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.khosravi.devin.present.databinding.ItemFilterBinding
import ir.khosravi.devin.present.tool.BaseAdapter

class FilterAdapter(
    private val itemListener: Listener,
) : BaseAdapter<FilterUiData, RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(position: ViewGroup, p1: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(position.context)
        return FilterItemViewHolder(ItemFilterBinding.inflate(inflater, position, false), ::onSelectChanged)
    }

    override fun onBindViewHolder(vh: RecyclerView.ViewHolder, position: Int) {
        val data = items[position]
        if (vh is FilterItemViewHolder) {
            data.isChecked = position == selectedIndex
            vh.bind(data)
        }
    }


    interface Listener {
        fun onNewFilterSelected(data: FilterUiData, newIndex: Int)
    }


    ///Selection part, TODO: try to decouple it to new class.
    private val adapter: BaseAdapter<FilterUiData, *> = this

    ///0 index linked to main item implicitly
    var selectedIndex: Int = 0
        private set

    private fun onSelectChanged(data: FilterUiData) {
        val newIndex = adapter.items.indexOf(data)
        checkSelect(newIndex)
    }

    private fun checkSelect(newIndex: Int) {
        if (newIndex == -1) {
            return
        }
        if (newIndex == selectedIndex) {
            return
        }
        val oldIndex = selectedIndex
        selectedIndex = newIndex
        onNewFilterSelected(oldIndex, newIndex, adapter.items[newIndex])
    }

    private fun onNewFilterSelected(oldIndex: Int, newIndex: Int, newItem: FilterUiData) {
        adapter.notifyItemChanged(oldIndex)
        adapter.notifyItemChanged(newIndex)
        itemListener.onNewFilterSelected(newItem, newIndex)
    }

    fun select(index: Int) {
        checkSelect(index)
    }

}