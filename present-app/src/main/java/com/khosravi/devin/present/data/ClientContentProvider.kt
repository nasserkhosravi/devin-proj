package com.khosravi.devin.present.data

import android.content.Context
import android.database.Cursor
import com.khosravi.devin.present.client.ClientData
import com.khosravi.devin.write.DevinContentProvider

object ClientContentProvider {

    fun getClientList(context: Context): List<ClientData> {
        val cursor =
            context.contentResolver.query(DevinContentProvider.uriOfClient(), null, null, null, null) ?: return emptyList()
        val result = ArrayList<ClientData>()
        while (cursor.moveToNext()) {
            val element = cursor.asClientData()
            result.add(element)
        }
        cursor.close()
        return result
    }

    private fun Cursor.asClientData() = ClientData(
        getString(0)
    )


}

