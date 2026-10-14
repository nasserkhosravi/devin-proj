package com.khosravi.devin.write.testing

import android.content.Context
import android.database.Cursor
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.DevinContentProvider
import com.khosravi.devin.write.DevinTool
import com.khosravi.devin.write.room.ClientTable
import com.khosravi.devin.write.room.DevinDB
import com.khosravi.devin.write.room.LogTable
import com.khosravi.devin.write.room.MetaIndexTable
import org.json.JSONObject
import org.robolectric.Robolectric
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Registers the real [DevinContentProvider] and reads back what landed in its Room DB. */
object DevinTestFixture {

    const val AUTHORITY = "com.khosravi.devin.provider"
    private val worker = Executors.newSingleThreadExecutor()

    /**
     * Runs [block] off the test (main) thread. Room rejects main-thread access, and in the app
     * provider calls arrive on binder threads anyway.
     */
    fun <T> onWorker(block: () -> T): T = worker.submit<T> { block() }.get(30, TimeUnit.SECONDS)

    fun registerProvider(): DevinContentProvider =
        Robolectric.setupContentProvider(DevinContentProvider::class.java, AUTHORITY)

    /** Forgets the process-wide [DevinDB] and [DevinTool] singletons so the next test starts fresh. */
    fun resetSingletons() {
        val db = DevinDB::class.java.getDeclaredField("sInstance").apply { isAccessible = true }
        (db.get(null) as DevinDB?)?.close()
        db.set(null, null)

        DevinTool::class.java.getDeclaredField("instance").apply {
            isAccessible = true
            set(null, null)
        }
    }

    fun logs(context: Context, clientId: String = context.packageName): List<LogRow> = onWorker {
        context.contentResolver.query(DevinUriHelper.getLogListUri(clientId), null, null, null, null)!!
            .use { it.toLogRows() }
    }

    fun clients(context: Context): List<Pair<String, String?>> = onWorker {
        context.contentResolver.query(DevinUriHelper.getClientListUri(), null, null, null, null)!!.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.string(ClientTable.COLUMN_PACKAGE_ID)!! to c.string(ClientTable.COLUMN_PRESENTER_CONFIG))
                }
            }
        }
    }

    fun metaIndexes(context: Context, logId: Long): List<Pair<String, String>> = onWorker {
        DevinDB.getInstance(context).openHelper.readableDatabase.query(
            "SELECT ${MetaIndexTable.COLUMN_FIELD_NAME}, ${MetaIndexTable.COLUMN_FIELD_VALUE} " +
                "FROM ${MetaIndexTable.TABLE_NAME} WHERE ${MetaIndexTable.COLUMN_ID} = ?",
            arrayOf<Any>(logId),
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
    }

    fun Cursor.toLogRows(): List<LogRow> = buildList {
        while (moveToNext()) {
            add(
                LogRow(
                    id = getLong(getColumnIndexOrThrow(LogTable.COLUMN_ID)),
                    tag = string(LogTable.COLUMN_TAG)!!,
                    value = string(LogTable.COLUMN_VALUE)!!,
                    date = getLong(getColumnIndexOrThrow(LogTable.COLUMN_DATE)),
                    meta = string(LogTable.COLUMN_META),
                    clientId = string(LogTable.COLUMN_CLIENT_ID)!!,
                    typeId = string(LogTable.COLUMN_TYPE_ID),
                )
            )
        }
    }

    private fun Cursor.string(column: String): String? = getColumnIndexOrThrow(column).let {
        if (isNull(it)) null else getString(it)
    }

    data class LogRow(
        val id: Long,
        val tag: String,
        val value: String,
        val date: Long,
        val meta: String?,
        val clientId: String,
        val typeId: String?,
    ) {
        fun metaJson() = JSONObject(meta!!)
    }
}
