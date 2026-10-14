package com.khosravi.devin.present.testing

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.khosravi.devin.write.room.ClientTable
import com.khosravi.devin.write.room.DevinDB
import com.khosravi.devin.write.room.LogTable
import com.khosravi.devin.write.room.MetaIndexTable

/** Helpers for tests that touch the shared Devin Room DB (owned by the `devin` module). */
object DevinDbFixture {

    /** Closes and forgets the process-wide [DevinDB] singleton so the next test starts from a fresh file. */
    fun resetSingleton() {
        val field = DevinDB::class.java.getDeclaredField("sInstance").apply { isAccessible = true }
        (field.get(null) as DevinDB?)?.close()
        field.set(null, null)
    }

    fun db(context: Context): SupportSQLiteDatabase = DevinDB.getInstance(context).openHelper.writableDatabase

    fun insertLog(
        db: SupportSQLiteDatabase,
        clientId: String,
        date: Long,
        tag: String = "tag",
        value: String = "value",
        id: Long? = null,
    ): Long = db.insert(
        LogTable.TABLE_NAME,
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            id?.let { put(LogTable.COLUMN_ID, it) }
            put(LogTable.COLUMN_TAG, tag)
            put(LogTable.COLUMN_VALUE, value)
            put(LogTable.COLUMN_DATE, date)
            put(LogTable.COLUMN_CLIENT_ID, clientId)
        },
    )

    fun insertClient(db: SupportSQLiteDatabase, clientId: String) {
        db.insert(
            ClientTable.TABLE_NAME,
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply { put(ClientTable.COLUMN_PACKAGE_ID, clientId) },
        )
    }

    fun insertMetaIndex(db: SupportSQLiteDatabase, logId: Long, field: String, value: String) {
        db.insert(
            MetaIndexTable.TABLE_NAME,
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put(MetaIndexTable.COLUMN_ID, logId)
                put(MetaIndexTable.COLUMN_FIELD_NAME, field)
                put(MetaIndexTable.COLUMN_FIELD_VALUE, value)
            },
        )
    }

    fun count(db: SupportSQLiteDatabase, sql: String, vararg args: Any?): Long =
        db.query(sql, args).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    fun logCount(db: SupportSQLiteDatabase, clientId: String? = null): Long =
        if (clientId == null) count(db, "SELECT COUNT(*) FROM ${LogTable.TABLE_NAME}")
        else count(db, "SELECT COUNT(*) FROM ${LogTable.TABLE_NAME} WHERE ${LogTable.COLUMN_CLIENT_ID} = ?", clientId)

    /** Dates of [clientId]'s logs, oldest first. */
    fun logDates(db: SupportSQLiteDatabase, clientId: String): List<Long> =
        db.query(
            "SELECT ${LogTable.COLUMN_DATE} FROM ${LogTable.TABLE_NAME} WHERE ${LogTable.COLUMN_CLIENT_ID} = ? " +
                "ORDER BY ${LogTable.COLUMN_DATE}",
            arrayOf<Any?>(clientId),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }

    /** Inserts many rows in one transaction; per-row autocommit is too slow for large fixtures. */
    fun inTransaction(db: SupportSQLiteDatabase, block: () -> Unit) {
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
