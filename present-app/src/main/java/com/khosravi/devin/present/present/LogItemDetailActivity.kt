package com.khosravi.devin.present.present

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.Toast
import com.khosravi.devin.present.R
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.databinding.ActivityLogItemDetailBinding
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.getLongExtraOrFail
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

    private lateinit var tag: String
    private lateinit var message: String
    private lateinit var timePresent: TimePresent
    private var meta: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        _binding = ActivityLogItemDetailBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        tag = intent.getStringExtra(EXTRA_TAG).orEmpty()
        message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        timePresent = TimePresent(intent.getLongExtraOrFail(EXTRA_TIMESTAMP))
        meta = intent.getStringExtra(EXTRA_META)

        binding.toolbar.setOnMenuItemClickListener(::onToolbarMenuItemClick)

        binding.run {
            tvTag.text = tag
            tvTime.text = getFormattedTimeWithMillis()
            tvMessage.text = message
            val metaText = meta
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
        val formatted = calendar.initIfNeed(timePresent).getFormatted()
        val millis = (timePresent.timestamp % 1000).toString().padStart(3, '0')
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
            appendLine("Tag: $tag")
            appendLine("Time: ${getFormattedTimeWithMillis()}")
            appendLine("Message: $message")
            meta?.let { appendLine("Meta: $it") }
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
        private const val EXTRA_TAG = "tag"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_TIMESTAMP = "timestamp"
        private const val EXTRA_META = "meta"

        fun startActivity(context: Context, data: TextLogItemData) {
            val intent = Intent(context, LogItemDetailActivity::class.java).apply {
                putExtra(EXTRA_TAG, data.tag)
                putExtra(EXTRA_MESSAGE, data.text)
                putExtra(EXTRA_TIMESTAMP, data.timePresent.timestamp)
                putExtra(EXTRA_META, data.meta?.toString())
            }
            context.startActivity(intent)
        }
    }
}
