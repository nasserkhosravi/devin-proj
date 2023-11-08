package ir.khosravi.devin.present.filter

import androidx.recyclerview.widget.RecyclerView
import ir.khosravi.devin.present.databinding.ItemFilterBinding

class FilterItemViewHolder(
    private val view: ItemFilterBinding,
    private val onClick: (data: FilterItem) -> Unit
) : RecyclerView.ViewHolder(view.root) {

    fun bind(data: FilterItem) = view.apply {
        chip.text = data.type
        chip.isSelected = data.isChecked

        chip.setOnClickListener {
            onClick(data)
        }
    }

}