package com.khosravi.devin.write.room

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface LogDao {

    @Query("SELECT * FROM " + LogTable.TABLE_NAME + " WHERE _clientId = :clientId ORDER BY _date DESC")
    fun getAllAsCursor(clientId: String): Cursor

    @Query("SELECT * FROM " + LogTable.TABLE_NAME + " WHERE _clientId = :clientId AND _tag = :tag ORDER BY _date DESC")
    fun getAllLogsByTagAsCursor(clientId: String, tag: String): Cursor

    @Query("SELECT * FROM " + LogTable.TABLE_NAME + " WHERE _id = :logId")
    fun getLog(logId: Long): Cursor

    @Insert
    fun insert(log: LogTable): Long

    @Update
    fun update(log: LogTable)

    @Query("DELETE FROM ${LogTable.TABLE_NAME} WHERE _clientId = :clientId")
    fun removeLogs(clientId: String)

}