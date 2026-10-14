package com.khosravi.devin.present.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.khosravi.devin.present.retention.RetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AppPrefTest {

    private lateinit var context: Context
    private lateinit var appPref: AppPref

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appPref = AppPref(context)
    }

    @Test
    fun `theme defaults to follow system and persists`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, appPref.theme)
        appPref.theme = AppCompatDelegate.MODE_NIGHT_YES
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppPref(context).theme)
    }

    @Test
    fun `retention defaults to the default policy`() {
        assertEquals(RetentionPolicy.DEFAULT, appPref.retentionPolicy)
        assertEquals(0L, appPref.lastVacuumAt)
    }

    @Test
    fun `retention settings persist and form the policy`() {
        appPref.retentionEnabled = false
        appPref.retentionDays = 30
        appPref.retentionMaxCount = 1_000
        appPref.lastVacuumAt = 42L

        val reloaded = AppPref(context)
        assertEquals(RetentionPolicy(false, 30, 1_000), reloaded.retentionPolicy)
        assertEquals(42L, reloaded.lastVacuumAt)
    }

    @Test
    fun `wrong password count increments per client and resets`() {
        assertEquals(0, appPref.getLastWrongPasswordCountWithConstrainCheck("a"))
        assertEquals(1, appPref.increaseLastWrongPasswordCount("a"))
        assertEquals(2, appPref.increaseLastWrongPasswordCount("a"))
        assertEquals(2, appPref.getLastWrongPasswordCountWithConstrainCheck("a"))
        assertEquals(0, appPref.getLastWrongPasswordCountWithConstrainCheck("b"))

        appPref.resetLastWrongPasswordCount("a")
        assertEquals(0, appPref.getLastWrongPasswordCountWithConstrainCheck("a"))
    }

    @Test
    fun `wrong password count expires after 8 hours`() {
        appPref.increaseLastWrongPasswordCount("a")
        appPref.increaseLastWrongPasswordCount("a")
        context.getSharedPreferences("AppPref", Context.MODE_PRIVATE).edit {
            putLong("a/KEY_WRONG_PASSWORD_DATE", Date().time - TimeUnit.HOURS.toMillis(9))
        }

        assertEquals(0, appPref.getLastWrongPasswordCountWithConstrainCheck("a"))
        assertEquals(1, appPref.increaseLastWrongPasswordCount("a"))
    }

    @Test
    fun `confirmed password is stored per client`() {
        assertNull(appPref.getLastConfirmedPassword("a"))
        appPref.saveConfirmedPassword("a", "secret")
        assertEquals("secret", appPref.getLastConfirmedPassword("a"))
        assertNull(appPref.getLastConfirmedPassword("b"))
    }

    @Test
    fun `update check result and time`() {
        appPref.saveUpdateCheckResult(100L, "4.6.0", "https://example.com/r")
        assertEquals(100L, appPref.lastUpdateCheckAt)
        assertEquals("4.6.0", appPref.latestReleaseVersion)
        assertEquals("https://example.com/r", appPref.latestReleaseUrl)

        appPref.saveUpdateCheckTime(200L)
        assertEquals(200L, appPref.lastUpdateCheckAt)
        assertEquals("4.6.0", appPref.latestReleaseVersion)
    }

    @Test
    fun `update prompt count and notified version`() {
        appPref.saveUpdatePromptCount(2026287, 2)
        assertEquals(2026287, appPref.updatePromptDay)
        assertEquals(2, appPref.updatePromptCount)

        assertNull(appPref.notifiedUpdateVersion)
        appPref.notifiedUpdateVersion = "4.6.0"
        assertEquals("4.6.0", appPref.notifiedUpdateVersion)
    }
}
