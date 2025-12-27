package com.khosravi.devin.present.log

import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.R
import com.khosravi.devin.present.databinding.ItemLogBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.gone
import com.khosravi.devin.present.tool.adapter.FastBindingItem
import com.khosravi.devin.present.visible

open class TextLogItem(
    private val calender: CalendarProxy,
    val data: TextLogItemData,
    val ignoreTagChip: Boolean? = null,
) : FastBindingItem<ItemLogBinding>() {

    override val type: Int = R.id.vh_item_text_log

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemLogBinding {
        return ItemLogBinding.inflate(inflater, parent, false)
    }

    override fun bindView(binding: ItemLogBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)
        val dateText = calender.initIfNeed(data.timePresent).getFormatted()
        val itemView = binding.root

        binding.run {
            timeText.text = dateText
            messageText.text = data.text
            if (ignoreTagChip == true) {
                tagChip.gone()
            } else {
                tagChip.text = data.tag
                tagChip.visible()
            }

            // Set colors and icon based on log level (Material 3 Expressive)
            when (data.logLevel) {
                Log.ERROR -> {
                    styleIt(
                        getString(R.string.log_item_error),
                        R.drawable.ic_info_24px,
                        R.color.log_error_icon,
                        R.color.log_error_icon_bg,
                        R.color.log_error_container,
                        R.color.log_error_text
                    )
                }

                Log.WARN -> {
                    styleIt(
                        getString(R.string.log_item_warn),
                        R.drawable.ic_warning_24px,
                        R.color.log_warning_icon,
                        R.color.log_warning_icon_bg,
                        R.color.log_warning_container,
                        R.color.log_warning_text
                    )
                }

                Log.INFO -> {
                    styleIt(
                        getString(R.string.log_item_info),
                        R.drawable.ic_info_24px,
                        R.color.log_info_icon,
                        R.color.log_info_icon_bg,
                        R.color.log_info_container,
                        R.color.log_info_text
                    )
                }

                Log.DEBUG, Log.VERBOSE -> {
                    val text = if (data.logLevel == Log.DEBUG) {
                        getString(R.string.log_item_debug)
                    } else {
                        getString(R.string.log_item_verbose)
                    }
                    styleIt(
                        text, R.drawable.ic_bug_report_24px,
                        R.color.log_debug_icon,
                        R.color.log_debug_icon_bg,
                        R.color.log_debug_container,
                        R.color.log_debug_text
                    )
                }
            }
        }
    }


    private fun ItemLogBinding.styleIt(
        logText: String,
        levelIconResource: Int,
        levelIconColorRes: Int,
        iconColorBgRes: Int,
        badgeBackgroundColorRes: Int,
        levelBadgeTextColorRes: Int
    ) {
        val iconBackground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
        }

        val badgeBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
        }

        val context = context
        levelBadge.text = logText
        levelIcon.setImageResource(levelIconResource)
        levelIcon.setColorFilter(
            ContextCompat.getColor(context, levelIconColorRes)
        )
        iconBackground.setColor(
            ContextCompat.getColor(context, iconColorBgRes)
        )
        badgeBackground.setColor(
            ContextCompat.getColor(context, badgeBackgroundColorRes)
        )
        levelBadge.setTextColor(
            ContextCompat.getColor(context, levelBadgeTextColorRes)
        )
        iconContainer.background = iconBackground
        levelBadge.background = badgeBackground
    }

}