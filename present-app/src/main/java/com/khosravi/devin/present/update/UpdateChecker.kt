package com.khosravi.devin.present.update

import android.content.Context
import android.util.Log
import com.khosravi.devin.present.BuildConfig
import com.khosravi.devin.present.data.AppPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.hours

/**
 * Checks GitHub for a newer presenter release at most once per [CHECK_INTERVAL_MILLIS] and
 * caches the result in [AppPref]; [availableUpdate] emits the cached newer release. Each new
 * version is announced once through [UpdateNotifier] (if notifications are allowed), and
 * separately prompted up to [MAX_PROMPTS_PER_DAY] times per calendar day, until updated, by the
 * caller of [pendingPrompt]/[markPrompted] (a dialog in `StarterActivity`).
 * The throttle timestamp is stored whenever GitHub answers (including non-200 responses such as
 * rate limiting), so only thrown errors (offline, malformed JSON) retry on the next trigger.
 */
class UpdateChecker(
    context: Context,
    private val appPref: AppPref,
    private val releaseSource: GitHubReleaseSource = GitHubReleaseSource(BuildConfig.VERSION_NAME),
    private val currentVersion: AppVersion? = AppVersion.parse(BuildConfig.VERSION_NAME),
) {

    private val notifier = UpdateNotifier(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isChecking = AtomicBoolean(false)

    private val _availableUpdate = MutableStateFlow(readAvailableUpdate())

    /** The cached newer release, or null if none or unknown. */
    val availableUpdate: StateFlow<ReleaseInfo?> = _availableUpdate.asStateFlow()

    fun checkInBackgroundIfDue() {
        if (currentVersion == null) return
        val elapsed = System.currentTimeMillis() - appPref.lastUpdateCheckAt
        // A negative elapsed time means the clock was rolled back; treat it as due.
        if (elapsed in 0 until CHECK_INTERVAL_MILLIS) return
        if (!isChecking.compareAndSet(false, true)) return
        scope.launch {
            try {
                val release = releaseSource.fetchLatestPresenterRelease()
                if (release == null) {
                    appPref.saveUpdateCheckTime(System.currentTimeMillis())
                } else {
                    appPref.saveUpdateCheckResult(System.currentTimeMillis(), release.version.toString(), release.pageUrl)
                }
                val update = readAvailableUpdate()
                _availableUpdate.value = update
                if (update != null) notifyOnce(update)
            } catch (exception: Exception) {
                Log.w(TAG, "Update check failed", exception)
            } finally {
                isChecking.set(false)
            }
        }
    }

    @Synchronized
    private fun notifyOnce(update: ReleaseInfo) {
        val version = update.version.toString()
        if (appPref.notifiedUpdateVersion != version && notifier.notify(update)) {
            appPref.notifiedUpdateVersion = version
        }
    }

    /** The update if today's prompt quota isn't used up; null otherwise. Does not count a prompt. */
    @Synchronized
    fun pendingPrompt(): ReleaseInfo? {
        val update = readAvailableUpdate() ?: return null
        return if (promptsShownToday() < MAX_PROMPTS_PER_DAY) update else null
    }

    /** Counts one answered prompt towards today's quota. */
    @Synchronized
    fun markPrompted() {
        appPref.saveUpdatePromptCount(today(), promptsShownToday() + 1)
    }

    private fun promptsShownToday(): Int =
        if (appPref.updatePromptDay == today()) appPref.updatePromptCount else 0

    /** Local calendar day as `yyyyDDD`; java.time's LocalDate needs API 26, minSdk is 24. */
    private fun today(): Int = Calendar.getInstance().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }

    private fun readAvailableUpdate(): ReleaseInfo? {
        val current = currentVersion ?: return null
        val latest = appPref.latestReleaseVersion?.let(AppVersion::parse) ?: return null
        if (latest <= current) return null
        return ReleaseInfo(
            version = latest,
            pageUrl = appPref.latestReleaseUrl ?: GitHubReleaseSource.RELEASES_PAGE_URL,
            isForceUpdate = latest.major > current.major,
        )
    }

    companion object {
        private const val TAG = "UpdateChecker"
        private val CHECK_INTERVAL_MILLIS = 24.hours.inWholeMilliseconds
        private const val MAX_PROMPTS_PER_DAY = 3
    }
}
