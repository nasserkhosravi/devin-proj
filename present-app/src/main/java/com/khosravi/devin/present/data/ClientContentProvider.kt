package com.khosravi.devin.present.data

import android.content.Context
import android.database.Cursor
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.present.toSafeJSONObject
import com.khosravi.devin.read.DevinUriHelper

object ClientContentProvider {

    fun getClientList(context: Context): List<ClientData> {
        val cursor =
            context.contentResolver.query(DevinUriHelper.getClientListUri(), null, null, null, null) ?: return emptyList()
        val result = ArrayList<ClientData>()
        while (cursor.moveToNext()) {
            val element = cursor.asClientData()
            result.add(element)
        }
        cursor.close()
        return result
    }

    fun getClient(context: Context, clientId: String): ClientData? {
        return getClientList(context).firstOrNull { it.packageId == clientId }
    }

    private fun Cursor.asClientData() = ClientData(
        getString(0),
        getString(1).toSafeJSONObject()
    )


}

