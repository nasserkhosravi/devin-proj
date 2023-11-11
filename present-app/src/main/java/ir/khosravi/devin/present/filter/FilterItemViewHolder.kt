package ir.khosravi.devin.present.filter

import androidx.recyclerview.widget.RecyclerView
import ir.khosravi.devin.present.databinding.ItemFilterBinding

class FilterItemViewHolder(
    private val view: ItemFilterBinding,
    private val onClick: (data: FilterUiData) -> Unit
) : RecyclerView.ViewHolder(view.root) {

    fun bind(data: FilterUiData) = view.apply {
        chip.text = data.title.value
        chip.isSelected = data.isChecked

        chip.setOnClickListener {
            onClick(data)
        }
    }

}