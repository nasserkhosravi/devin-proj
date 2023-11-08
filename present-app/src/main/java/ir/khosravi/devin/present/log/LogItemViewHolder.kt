package ir.khosravi.devin.present.log

import androidx.recyclerview.widget.RecyclerView
import ir.khosravi.devin.present.databinding.ItemLogBinding
import ir.khosravi.devin.present.withPadding
import java.util.Calendar
import java.util.Date

class LogItemViewHolder(
    private val view: ItemLogBinding
) : RecyclerView.ViewHolder(view.root) {

    fun bind(data: LogItem) = view.apply {
        val dataText = getDateText(data)
        tvText.text = dataText.plus(" ${data.text}")
    }

    private fun getDateText(data: LogItem): String {
        val calendar = Calendar.getInstance().apply {
            time = Date(data.dateTimeStamp)
        }
        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).withPadding()
        val day = calendar.get(Calendar.DAY_OF_MONTH).withPadding()

        val hour = calendar.get(Calendar.HOUR_OF_DAY).withPadding()
        val minute = calendar.get(Calendar.MINUTE).withPadding()
        val second = calendar.get(Calendar.SECOND).withPadding()

        val dataText = "$year/$month/$day $hour:$minute:$second"
        return dataText
    }

}