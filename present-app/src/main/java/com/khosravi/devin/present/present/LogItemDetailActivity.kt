package com.khosravi.devin.present.present

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.date.CalendarProxy
import com.khosravi.devin.present.date.TimePresent
import com.khosravi.devin.present.date.getFormattedWithMillis
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.getLongExtraOrFail
import com.khosravi.devin.present.log.TextLogItemData
import com.khosravi.devin.present.uikit.theme.DevinTheme
import javax.inject.Inject

/**
 * Bridge host for [LogItemDetailScreen], used only by [ImportLogActivity]. [LogActivity] shows the
 * same screen as a navigation destination instead. Delete when ImportLogActivity is migrated.
 */
class LogItemDetailActivity : BaseActivity() {

    @Inject
    lateinit var calendar: CalendarProxy

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)

        val tag = intent.getStringExtra(EXTRA_TAG).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val timeText = calendar.getFormattedWithMillis(TimePresent(intent.getLongExtraOrFail(EXTRA_TIMESTAMP)))
        val meta = intent.getStringExtra(EXTRA_META)

        setContent {
            DevinTheme {
                LogItemDetailScreen(
                    tag = tag,
                    message = message,
                    timeText = timeText,
                    meta = meta,
                    onCopy = { copyLogDetail(tag, timeText, message, meta) },
                    onBack = ::finish,
                )
            }
        }
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
