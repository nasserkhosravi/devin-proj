package com.khosravi.devin.present.retention

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.khosravi.devin.write.room.ClientTable
import com.khosravi.devin.write.room.LogTable
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Applies a [RetentionPolicy] to the shared Devin DB and the per-log content files.
 *
 * No Android lifecycle here: the DB, the files dir and the clock are injected so this can be
 * unit-tested later.
 *
 * Row deletion is batched ([DELETE_BATCH_SIZE] rows per autocommitted statement), not atomic, so
 * client apps' synchronous provider inserts are never blocked behind one long transaction. A
 * failure propagates to the caller and leaves earlier batches committed; the next trim continues
 * from there. The file sweep and `VACUUM` are best-effort and only log, since the next trim
 * retries them.
 */
class RetentionCleaner(
    private val db: SupportSQLiteDatabase,
    private val filesDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {

    data class TrimResult(
        val deletedRows: Int,
        val deletedContentDirs: Int,
        val vacuumed: Boolean,
    )

    /** @param lastVacuumAt epoch millis of the previous `VACUUM`; caller persists the new time when [TrimResult.vacuumed]. */
    fun trim(policy: RetentionPolicy, lastVacuumAt: Long): TrimResult {
        val deletedRows = deleteExpiredRows(policy)
        val deletedDirs = try {
            sweepOrphanContentDirs()
        } catch (e: Exception) {
            Log.w(TAG, "Orphan content sweep failed", e)
            0
        }
        val vacuumed = try {
            vacuumIfWorthIt(lastVacuumAt)
        } catch (e: Exception) {
            Log.w(TAG, "VACUUM failed", e)
            false
        }
        return TrimResult(deletedRows, deletedDirs, vacuumed)
    }

    private fun deleteExpiredRows(policy: RetentionPolicy): Int =
        deleteOlderThanMaxAge(policy.maxAgeDays) + deleteBeyondCountCap(policy.maxCountPerClient)

    private fun deleteOlderThanMaxAge(maxAgeDays: Int): Int {
        val cutoff = now() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
        var total = 0
        while (true) {
            val deleted = deleteBatch(
                "$COL_ID IN (SELECT $COL_ID FROM ${LogTable.TABLE_NAME} WHERE $COL_DATE < ? LIMIT ?)",
                arrayOf<Any?>(cutoff, DELETE_BATCH_SIZE.toLong()),
            )
            if (deleted == 0) break
            total += deleted
        }
        return total
    }

    private fun deleteBeyondCountCap(maxCountPerClient: Int): Int {
        var total = 0
        for (clientId in queryLogClientIds()) {
            val count = countLogs(clientId)
            if (count <= maxCountPerClient) continue
            var remaining = count - maxCountPerClient
            // Oldest-first batches: removing the oldest `excess` rows is equivalent to keeping the newest N.
            while (remaining > 0) {
                val deleted = deleteBatch(
                    "$COL_ID IN (SELECT $COL_ID FROM ${LogTable.TABLE_NAME} WHERE $COL_CLIENT_ID = ? " +
                        "ORDER BY $COL_DATE ASC LIMIT ?)",
                    arrayOf<Any?>(clientId, minOf(DELETE_BATCH_SIZE.toLong(), remaining)),
                )
                if (deleted == 0) break
                total += deleted
                remaining -= deleted
            }
        }
        return total
    }

    /** One bounded `DELETE`; outside an explicit transaction it autocommits, keeping the write lock short. */
    private fun deleteBatch(whereClause: String, args: Array<Any?>): Int =
        db.delete(LogTable.TABLE_NAME, whereClause, args)

    private fun countLogs(clientId: String): Long =
        db.query("SELECT COUNT(*) FROM ${LogTable.TABLE_NAME} WHERE $COL_CLIENT_ID = ?", arrayOf<Any?>(clientId))
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }

    /** Deletes `filesDir/<clientId>/<logId>/` dirs whose log row no longer exists. Only registered clients' dirs are scanned. */
    private fun sweepOrphanContentDirs(): Int {
        var deleted = 0
        db.compileStatement("SELECT EXISTS(SELECT 1 FROM ${LogTable.TABLE_NAME} WHERE $COL_ID = ?)").use { exists ->
            for (clientId in queryRegisteredClientIds()) {
                val logDirs = File(filesDir, clientId).listFiles { file -> file.isDirectory } ?: continue
                for (logDir in logDirs) {
                    val logId = logDir.name.toLongOrNull() ?: continue
                    exists.bindLong(1, logId)
                    val logExists = exists.simpleQueryForLong() != 0L
                    if (!logExists && logDir.deleteRecursively()) {
                        deleted++
                    }
                }
            }
        }
        return deleted
    }

    /**
     * Android's SQLite usually defaults to `auto_vacuum=FULL`, which keeps `freelist_count` near 0,
     * so this mostly acts as a fallback for databases created without auto-vacuum.
     */
    private fun vacuumIfWorthIt(lastVacuumAt: Long): Boolean {
        if (now() - lastVacuumAt < VACUUM_INTERVAL_MILLIS) return false
        val pageCount = pragmaLong("page_count")
        if (pageCount == 0L) return false
        val freePages = pragmaLong("freelist_count")
        val freeBytes = freePages * pragmaLong("page_size")
        if (freePages.toDouble() / pageCount <= VACUUM_MIN_FREE_RATIO) return false
        if (freeBytes <= VACUUM_MIN_FREE_BYTES) return false
        db.execSQL("VACUUM")
        // In WAL mode the shrink only reaches the main DB file after a checkpoint.
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        return true
    }

    private fun queryLogClientIds(): List<String> =
        queryStrings("SELECT DISTINCT $COL_CLIENT_ID FROM ${LogTable.TABLE_NAME}")

    private fun queryRegisteredClientIds(): List<String> =
        queryStrings("SELECT ${ClientTable.COLUMN_PACKAGE_ID} FROM ${ClientTable.TABLE_NAME}")

    private fun queryStrings(sql: String): List<String> = db.query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let(::add)
            }
        }
    }

    private fun pragmaLong(name: String): Long =
        db.query("PRAGMA $name").use { if (it.moveToFirst()) it.getLong(0) else 0L }

    private companion object {
        const val TAG = "RetentionCleaner"
        const val COL_ID = LogTable.COLUMN_ID
        const val COL_DATE = LogTable.COLUMN_DATE
        const val COL_CLIENT_ID = LogTable.COLUMN_CLIENT_ID

        const val DELETE_BATCH_SIZE = 5_000

        val VACUUM_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)
        const val VACUUM_MIN_FREE_RATIO = 0.25
        const val VACUUM_MIN_FREE_BYTES = 1L * 1024 * 1024
    }
}
