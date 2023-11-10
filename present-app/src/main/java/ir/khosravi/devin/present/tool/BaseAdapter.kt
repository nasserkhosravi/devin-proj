package ir.khosravi.devin.present.tool

import androidx.recyclerview.widget.RecyclerView

abstract class BaseAdapter<D, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {

    private val mItems = ArrayList<D>()

    val items: List<D>
        get() = mItems

    override fun getItemCount(): Int = mItems.size

    fun addAll(newItems: List<D>) {
        val lastIndex = mItems.lastIndex
        mItems.addAll(newItems)
        notifyItemRangeInserted(lastIndex, newItems.size)
    }

    fun replaceAll(newItems: List<D>) {
        mItems.clear()
        mItems.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeAll() {
        mItems.clear()
        notifyDataSetChanged()
    }

}