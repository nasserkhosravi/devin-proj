package com.khosravi.devin.present.data

import android.content.Context
import androidx.core.content.edit
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

class AppPref @Inject constructor(appContext: Context) {

    private val pref = appContext.getSharedPreferences("AppPref", Context.MODE_PRIVATE)

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
        private const val KEY_LAST_CONFIRMED_PASSWORD = "LAST_CONFIRMED_PASSWORD"
        private const val KEY_WRONG_PASSWORD_COUNT = "KEY_WRONG_PASSWORD_COUNT"
        private const val KEY_WRONG_PASSWORD_DATE = "KEY_WRONG_PASSWORD_DATE"
    }
}