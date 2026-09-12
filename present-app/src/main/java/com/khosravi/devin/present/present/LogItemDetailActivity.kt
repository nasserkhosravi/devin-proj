package com.khosravi.devin.present.present

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.Toast
import com.khosravi.devin.present.KEY_DATA
import com.khosravi.devin.present.R
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.databinding.ActivityLogItemDetailBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.getSerializableSupport
import com.khosravi.devin.present.gone
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.setClipboardSafe
import com.khosravi.devin.present.visible
import javax.inject.Inject

class LogItemDetailActivity : BaseActivity() {

    private var _binding: ActivityLogItemDetailBinding? = null
    private val binding: ActivityLogItemDetailBinding
        get() = _binding!!

    @Inject
    lateinit var calendar: CalendarProxy

    private lateinit var data: TextLogItemData

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityLogItemDetailBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        data = intent.extras?.getSerializableSupport(KEY_DATA, TextLogItemData::class.java)!!

        binding.toolbar.setOnMenuItemClickListener(::onToolbarMenuItemClick)

        binding.run {
            tvTag.text = data.tag
            tvTime.text = getFormattedTimeWithMillis()
            tvMessage.text = data.text
            val metaText = data.meta?.toString()
            if (metaText != null) {
                tvMeta.text = metaText
                tvMeta.visible()
                labelMeta.visible()
            } else {
                tvMeta.gone()
                labelMeta.gone()
            }
        }
    }

    private fun getFormattedTimeWithMillis(): String {
        val formatted = calendar.initIfNeed(data.timePresent).getFormatted()
        val millis = (data.timePresent.timestamp % 1000).toString().padStart(3, '0')
        return "$formatted.$millis"
    }

    private fun onToolbarMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy_log_detail -> {
                copyLogDetail()
                true
            }

            else -> false
        }
    }

    private fun copyLogDetail() {
        val content = buildString {
            appendLine("Tag: ${data.tag}")
            appendLine("Time: ${getFormattedTimeWithMillis()}")
            appendLine("Message: ${data.text}")
            data.meta?.let { appendLine("Meta: $it") }
        }
        if (setClipboardSafe(content)) {
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun startActivity(context: Context, data: TextLogItemData) {
            val intent = Intent(context, LogItemDetailActivity::class.java).apply {
                putExtra(KEY_DATA, data)
            }
            context.startActivity(intent)
        }
    }
}
