package com.khosravi.devin.present.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.data.AppPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * Covers the cached-release and prompt-quota logic. [UpdateChecker.checkInBackgroundIfDue] hits
 * GitHub, so it is only exercised where it returns before any network call.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {

    private lateinit var context: Context
    private lateinit var appPref: AppPref

    private val installed = AppVersion(4, 5, 0)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appPref = AppPref(context)
    }

    private fun checker(currentVersion: AppVersion? = installed) =
        UpdateChecker(context, appPref, GitHubReleaseSource("test"), currentVersion)

    private fun cacheLatest(version: String, url: String = "https://example.com/$version") =
        appPref.saveUpdateCheckResult(System.currentTimeMillis(), version, url)

    private fun today(): Int = Calendar.getInstance().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }

    @Test
    fun `no cached release means no update`() {
        assertNull(checker().availableUpdate.value)
        assertNull(checker().pendingPrompt())
    }

    @Test
    fun `newer cached release is available`() {
        cacheLatest("4.6.0")
        val update = checker().availableUpdate.value!!
        assertEquals(AppVersion(4, 6, 0), update.version)
        assertEquals("https://example.com/4.6.0", update.pageUrl)
        assertFalse(update.isForceUpdate)
    }

    @Test
    fun `same or older cached release is not an update`() {
        cacheLatest("4.5.0")
        assertNull(checker().availableUpdate.value)
        cacheLatest("4.4.9")
        assertNull(checker().availableUpdate.value)
    }

    @Test
    fun `major bump is a force update`() {
        cacheLatest("5.0.0")
        assertTrue(checker().availableUpdate.value!!.isForceUpdate)
    }

    @Test
    fun `unknown installed version means no update`() {
        cacheLatest("9.9.9")
        assertNull(checker(currentVersion = null).availableUpdate.value)
    }

    @Test
    fun `unparseable cached version means no update`() {
        appPref.saveUpdateCheckResult(0L, "garbage", "https://example.com")
        assertNull(checker().availableUpdate.value)
    }

    @Test
    fun `prompt is offered three times per day`() {
        cacheLatest("4.6.0")
        val checker = checker()

        repeat(3) {
            assertEquals(AppVersion(4, 6, 0), checker.pendingPrompt()?.version)
            checker.markPrompted()
        }

        assertNull(checker.pendingPrompt())
        assertEquals(today(), appPref.updatePromptDay)
        assertEquals(3, appPref.updatePromptCount)
    }

    @Test
    fun `pendingPrompt does not count as a prompt`() {
        cacheLatest("4.6.0")
        val checker = checker()
        repeat(5) { checker.pendingPrompt() }
        assertEquals(0, appPref.updatePromptCount)
    }

    @Test
    fun `quota from another day does not count`() {
        cacheLatest("4.6.0")
        appPref.saveUpdatePromptCount(today() - 1, 3)

        val checker = checker()
        assertEquals(AppVersion(4, 6, 0), checker.pendingPrompt()?.version)
        checker.markPrompted()
        assertEquals(1, appPref.updatePromptCount)
    }

    @Test
    fun `check is skipped without an installed version`() {
        checker(currentVersion = null).checkInBackgroundIfDue()
        assertEquals(0L, appPref.lastUpdateCheckAt)
    }

    @Test
    fun `check is skipped when the last one was recent`() {
        appPref.saveUpdateCheckTime(System.currentTimeMillis())
        val before = appPref.lastUpdateCheckAt

        checker().checkInBackgroundIfDue()

        assertEquals(before, appPref.lastUpdateCheckAt)
    }

    @Test
    fun `release page texts depend on force update`() {
        val available = ReleaseInfo(AppVersion(4, 6, 0), "u")
        val required = ReleaseInfo(AppVersion(5, 0, 0), "u", isForceUpdate = true)

        assertEquals("Devin update available", context.updateTitle(available))
        assertEquals("Devin update required", context.updateTitle(required))
        assertTrue(context.updateMessage(available).startsWith("Version 4.6.0 is available"))
        assertTrue(context.updateMessage(required).startsWith("Version 5.0.0 is a major update"))
    }
}
