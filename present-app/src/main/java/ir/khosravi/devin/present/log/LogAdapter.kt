package ir.khosravi.devin.present.log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.khosravi.devin.present.databinding.ItemLogBinding
import ir.khosravi.devin.present.tool.BaseAdapter

class LogAdapter : BaseAdapter<LogItem,RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(p0.context)
        return LogItemViewHolder(ItemLogBinding.inflate(inflater, p0, false))
    }

    override fun onBindViewHolder(vh: RecyclerView.ViewHolder, position: Int) {
        val data = items[position]
        if (vh is LogItemViewHolder) {
            vh.bind(data)
        }
    }

}