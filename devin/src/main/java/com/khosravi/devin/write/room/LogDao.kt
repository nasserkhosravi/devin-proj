package com.khosravi.devin.write.room

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogDao {

    @Query("SELECT * FROM " + LogTable.TABLE_NAME)
    fun getAllAsCursor(): Cursor

    @Insert
    fun insert(log: LogTable): Long

    @Query("DELETE FROM ${LogTable.TABLE_NAME}")
    fun nukeTable()

}