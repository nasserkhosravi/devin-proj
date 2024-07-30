package com.khosravi.devin.present.data

import android.content.Context
import com.khosravi.devin.present.client.ClientData
import javax.inject.Inject

class CacheRepository @Inject constructor(
    appContext: Context
) {

    private val pref = appContext.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun setSelectedClientId(clientData: ClientData) {
        pref.edit()
            .putString(FIELD_CLIENT_ID, clientData.packageId)
            .commit()
    }

    fun getSelectedClientId(): String? = pref.getString(FIELD_CLIENT_ID, null)

    companion object {
        private const val PREF_NAME = "appCache"
        private const val FIELD_CLIENT_ID = "_clientId"
    }
}