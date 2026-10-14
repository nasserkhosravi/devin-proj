package com.khosravi.devin.present.present.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.khosravi.devin.present.R
import com.khosravi.devin.present.arch.BaseActivity
import com.khosravi.devin.present.data.FilterRepository
import com.khosravi.devin.present.databinding.ActivitySettingsBinding
import com.khosravi.devin.present.di.getAppComponent
import com.khosravi.devin.present.retention.RetentionObserver
import com.khosravi.devin.present.retention.RetentionPolicy
import com.khosravi.devin.present.retention.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import javax.inject.Inject

class SettingsActivity : BaseActivity() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding: ActivitySettingsBinding
        get() = _binding!!

    @Inject
    lateinit var retentionObserver: RetentionObserver

    @Inject
    lateinit var filterRepository: FilterRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        getAppComponent().inject(this)
        super.onCreate(savedInstanceState)
        _binding = ActivitySettingsBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.switchRetention.isChecked = appPref.retentionEnabled
        binding.switchRetention.setOnCheckedChangeListener { _, isChecked ->
            appPref.retentionEnabled = isChecked
            onRetentionChanged()
        }
        binding.rowKeepDays.setOnClickListener { showKeepDaysPicker() }
        binding.rowMaxCount.setOnClickListener { showMaxCountPicker() }

        bindRetentionValues()
        loadStorageUsage()
        bindPinnedTags()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun onRetentionChanged() {
        retentionObserver.resetThrottle()
        bindRetentionValues()
    }

    private fun bindRetentionValues() {
        val isEnabled = appPref.retentionEnabled
        binding.tvKeepDaysValue.text = formatDays(appPref.retentionDays)
        binding.tvMaxCountValue.text = formatCount(appPref.retentionMaxCount)
        listOf(binding.rowKeepDays, binding.rowMaxCount).forEach { it.setEnabledLook(isEnabled) }
    }

    private fun showKeepDaysPicker() {
        val options = RetentionPolicy.AGE_OPTIONS_DAYS
        showPicker(
            titleRes = R.string.settings_keep_logs_for,
            labels = options.map(::formatDays),
            checkedIndex = options.indexOf(appPref.retentionDays),
        ) { index ->
            appPref.retentionDays = options[index]
            onRetentionChanged()
        }
    }

    private fun showMaxCountPicker() {
        val options = RetentionPolicy.COUNT_OPTIONS
        showPicker(
            titleRes = R.string.settings_max_logs_per_client,
            labels = options.map(::formatCount),
            checkedIndex = options.indexOf(appPref.retentionMaxCount),
        ) { index ->
            appPref.retentionMaxCount = options[index]
            onRetentionChanged()
        }
    }

    private fun showPicker(titleRes: Int, labels: List<String>, checkedIndex: Int, onPicked: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                onPicked(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun loadStorageUsage() {
        launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { StorageUsage.computeBytes(applicationContext) }
                    .onFailure { Log.w(TAG, "Failed to compute storage usage", it) }
                    .getOrNull()
            }
            _binding?.tvStorageValue?.text =
                bytes?.let { Formatter.formatShortFileSize(this@SettingsActivity, it) } ?: STORAGE_UNKNOWN
        }
    }

    private fun bindPinnedTags() {
        val group = binding.chipGroupPinnedTags
        group.removeAllViews()
        filterRepository.getPinnedTags().forEach { tag ->
            group.addView(Chip(this).apply {
                text = tag
                isCheckable = false
                isCloseIconVisible = true
                closeIconContentDescription = getString(R.string.settings_pinned_tags_remove, tag)
                setOnCloseIconClickListener {
                    filterRepository.unpinTag(tag)
                    bindPinnedTags()
                }
            })
        }
        group.addView(Chip(this).apply {
            setText(R.string.settings_pinned_tags_add)
            isCheckable = false
            setOnClickListener { showAddPinnedTagDialog() }
        })
    }

    private fun showAddPinnedTagDialog() {
        val input = EditText(this).apply {
            setHint(R.string.settings_pinned_tags_add_hint)
            isSingleLine = true
        }
        val padding = (DIALOG_INPUT_PADDING_DP * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_pinned_tags_add)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                filterRepository.pinTag(input.text.toString())
                bindPinnedTags()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatDays(days: Int): String =
        resources.getQuantityString(R.plurals.settings_retention_days, days, days)

    private fun formatCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)

    private fun View.setEnabledLook(isEnabled: Boolean) {
        this.isEnabled = isEnabled
        alpha = if (isEnabled) 1f else DISABLED_ALPHA
    }

    companion object {
        private const val TAG = "SettingsActivity"
        private const val DISABLED_ALPHA = 0.38f
        private const val STORAGE_UNKNOWN = "—"
        private const val DIALOG_INPUT_PADDING_DP = 24

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
