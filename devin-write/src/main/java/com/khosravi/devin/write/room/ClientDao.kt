package com.khosravi.devin.write.room

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ClientDao {

    @Query("SELECT * FROM " + ClientTable.TABLE_NAME)
    fun getAllAsCursor(): Cursor

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(log: ClientTable): Long

    @Query("DELETE FROM ${ClientTable.TABLE_NAME}")
    fun nukeTable()

}