package com.khosravi.devin.present.retention

import android.content.Context
import androidx.annotation.WorkerThread
import com.khosravi.devin.present.data.ClientContentProvider
import com.khosravi.devin.write.room.DevinDB
import java.io.File

object StorageUsage {

    /** Bytes used by the Devin DB (incl. WAL/SHM) plus all registered clients' content files. */
    @WorkerThread
    fun computeBytes(context: Context): Long {
        val dbName = DevinDB.getInstance(context).openHelper.databaseName ?: return 0L
        val dbPath = context.getDatabasePath(dbName).path
        val dbBytes = listOf("", "-wal", "-shm").sumOf { suffix -> File(dbPath + suffix).length() }
        val contentBytes = ClientContentProvider.getClientList(context).sumOf { client ->
            File(context.filesDir, client.packageId).walkBottomUp()
                .filter { it.isFile }
                .sumOf { it.length() }
        }
        return dbBytes + contentBytes
    }
}
