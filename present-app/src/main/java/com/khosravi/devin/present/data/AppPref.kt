package com.khosravi.devin.present.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

class AppPref @Inject constructor(appContext: Context) {

    private val pref = appContext.getSharedPreferences("AppPref", Context.MODE_PRIVATE)

    var theme: Int
        get() = pref.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = pref.edit { putInt(KEY_THEME, value) }

    val lastUpdateCheckAt: Long
        get() = pref.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)

    val latestReleaseVersion: String?
        get() = pref.getString(KEY_LATEST_RELEASE_VERSION, null)

    val latestReleaseUrl: String?
        get() = pref.getString(KEY_LATEST_RELEASE_URL, null)

    /** Day key (see `UpdateChecker`) of [updatePromptCount]. */
    val updatePromptDay: Int
        get() = pref.getInt(KEY_UPDATE_PROMPT_DAY, 0)

    val updatePromptCount: Int
        get() = pref.getInt(KEY_UPDATE_PROMPT_COUNT, 0)

    fun saveUpdatePromptCount(day: Int, count: Int) {
        pref.edit {
            putInt(KEY_UPDATE_PROMPT_DAY, day)
            putInt(KEY_UPDATE_PROMPT_COUNT, count)
        }
    }

    var notifiedUpdateVersion: String?
        get() = pref.getString(KEY_NOTIFIED_UPDATE_VERSION, null)
        set(value) = pref.edit { putString(KEY_NOTIFIED_UPDATE_VERSION, value) }

    /** Stamps the update check time and stores the latest release, all in one write. */
    fun saveUpdateCheckResult(checkedAt: Long, latestVersion: String, latestUrl: String) = pref.edit {
        putLong(KEY_LAST_UPDATE_CHECK_AT, checkedAt)
        putString(KEY_LATEST_RELEASE_VERSION, latestVersion)
        putString(KEY_LATEST_RELEASE_URL, latestUrl)
    }

    /** Stamps the update check time, keeping the previously cached latest release. */
    fun saveUpdateCheckTime(checkedAt: Long) = pref.edit { putLong(KEY_LAST_UPDATE_CHECK_AT, checkedAt) }

    fun getLastWrongPasswordCountWithConstrainCheck(clientId: String): Int {
        val now = Date().time
        val lastDate = pref.getLong(clientId.plus("/$KEY_WRONG_PASSWORD_DATE"), 0)
        if (lastDate == 0L) {
            return pref.getInt(clientId.plus("/$KEY_WRONG_PASSWORD_COUNT"), 0)
        }
        val constraintDate = lastDate + 8.hours.inWholeMilliseconds
        val isConstraintExpired = constraintDate < now
        if (isConstraintExpired) {
            resetLastWrongPasswordCount(clientId)
            return 0
        } else {
            return pref.getInt(clientId.plus("/$KEY_WRONG_PASSWORD_COUNT"), 0)
        }
    }

    fun resetLastWrongPasswordCount(clientId: String) {
        pref.edit {
            putInt(clientId.plus("/$KEY_WRONG_PASSWORD_COUNT"), 0)
            putLong(clientId.plus("/$KEY_WRONG_PASSWORD_DATE"), 0)
        }
    }

    fun increaseLastWrongPasswordCount(clientId: String): Int {
        val key = clientId.plus("/$KEY_WRONG_PASSWORD_COUNT")
        val newCount = pref.getInt(key, 0) +1
        pref.edit {
            putInt(key, newCount)
            putLong(clientId.plus("/$KEY_WRONG_PASSWORD_DATE"), Date().time)
        }
        return newCount
    }

    fun getLastConfirmedPassword(clientId: String): String? {
        return pref.getString(clientId.plus("/$KEY_LAST_CONFIRMED_PASSWORD"), null)
    }

    fun saveConfirmedPassword(clientId: String, password: String) {
        pref.edit { putString(clientId.plus("/$KEY_LAST_CONFIRMED_PASSWORD"), password) }
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_LAST_CONFIRMED_PASSWORD = "LAST_CONFIRMED_PASSWORD"
        private const val KEY_WRONG_PASSWORD_COUNT = "KEY_WRONG_PASSWORD_COUNT"
        private const val KEY_WRONG_PASSWORD_DATE = "KEY_WRONG_PASSWORD_DATE"
        private const val KEY_LAST_UPDATE_CHECK_AT = "LAST_UPDATE_CHECK_AT"
        private const val KEY_LATEST_RELEASE_VERSION = "LATEST_RELEASE_VERSION"
        private const val KEY_LATEST_RELEASE_URL = "LATEST_RELEASE_URL"
        private const val KEY_UPDATE_PROMPT_DAY = "UPDATE_PROMPT_DAY"
        private const val KEY_UPDATE_PROMPT_COUNT = "UPDATE_PROMPT_COUNT"
        private const val KEY_NOTIFIED_UPDATE_VERSION = "NOTIFIED_UPDATE_VERSION"
    }
}