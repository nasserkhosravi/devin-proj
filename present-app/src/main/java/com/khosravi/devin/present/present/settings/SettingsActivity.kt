package com.khosravi.devin.present.present.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.khosravi.devin.present.R
import com.khosravi.devin.present.analytics.Analytics
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.retention.RetentionObserver
import com.khosravi.devin.present.retention.RetentionPolicy
import com.khosravi.devin.present.retention.StorageUsage
import com.khosravi.devin.present.uikit.theme.DevinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import javax.inject.Inject

class SettingsActivity : BaseActivity() {

    @Inject
    lateinit var retentionObserver: RetentionObserver

    @Inject
    lateinit var filterRepository: FilterRepository

    private var retentionEnabled by mutableStateOf(false)
    private var retentionDays by mutableStateOf(0)
    private var retentionMaxCount by mutableStateOf(0)
    private var pinnedTags by mutableStateOf(emptyList<String>())

    /** `null` while calculating. */
    private var storageText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) Analytics.settingsOpened()

        refreshRetentionValues()
        refreshPinnedTags()

        setContent {
            val keepDays = remember(retentionDays) {
                choice(retentionDays, RetentionPolicy.AGE_OPTIONS_DAYS, ::formatDays)
            }
            val maxCount = remember(retentionMaxCount) {
                choice(retentionMaxCount, RetentionPolicy.COUNT_OPTIONS, ::formatCount)
            }
            DevinTheme {
                SettingsScreen(
                    retentionEnabled = retentionEnabled,
                    keepDays = keepDays,
                    maxCount = maxCount,
                    storageText = storageText,
                    pinnedTags = pinnedTags,
                    onRetentionEnabledChange = ::onRetentionEnabledChange,
                    onKeepDaysPicked = ::onKeepDaysPicked,
                    onMaxCountPicked = ::onMaxCountPicked,
                    onUnpinTag = ::onUnpinTag,
                    onPinTag = ::onPinTag,
                    onBack = ::finish,
                )
            }
        }

        loadStorageUsage()
    }

    private fun choice(current: Int, options: List<Int>, format: (Int) -> String) =
        SettingsChoice(value = format(current), labels = options.map(format), selectedIndex = options.indexOf(current))

    private fun onRetentionEnabledChange(isEnabled: Boolean) {
        appPref.retentionEnabled = isEnabled
        Analytics.retentionToggled(isEnabled)
        onRetentionChanged()
    }

    private fun onKeepDaysPicked(index: Int) {
        appPref.retentionDays = RetentionPolicy.AGE_OPTIONS_DAYS[index]
        Analytics.retentionDaysChanged(appPref.retentionDays)
        onRetentionChanged()
    }

    private fun onMaxCountPicked(index: Int) {
        appPref.retentionMaxCount = RetentionPolicy.COUNT_OPTIONS[index]
        Analytics.retentionMaxCountChanged(appPref.retentionMaxCount)
        onRetentionChanged()
    }

    private fun onRetentionChanged() {
        retentionObserver.resetThrottle()
        refreshRetentionValues()
    }

    private fun refreshRetentionValues() {
        retentionEnabled = appPref.retentionEnabled
        retentionDays = appPref.retentionDays
        retentionMaxCount = appPref.retentionMaxCount
    }

    private fun onUnpinTag(tag: String) {
        filterRepository.unpinTag(tag)
        Analytics.pinnedTagRemoved()
        refreshPinnedTags()
    }

    private fun onPinTag(text: String) {
        filterRepository.pinTag(text)
        if (text.isNotBlank()) Analytics.pinnedTagAdded()
        refreshPinnedTags()
    }

    private fun refreshPinnedTags() {
        pinnedTags = filterRepository.getPinnedTags()
    }

    private fun loadStorageUsage() {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { StorageUsage.computeBytes(applicationContext) }
                    .onFailure { Log.w(TAG, "Failed to compute storage usage", it) }
                    .getOrNull()
            }
            storageText = bytes?.let { Formatter.formatShortFileSize(this@SettingsActivity, it) } ?: STORAGE_UNKNOWN
        }
    }

    private fun formatDays(days: Int): String =
        resources.getQuantityString(R.plurals.settings_retention_days, days, days)

    private fun formatCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)

    companion object {
        private const val TAG = "SettingsActivity"
        private const val STORAGE_UNKNOWN = "—"

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
