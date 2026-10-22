package com.khosravi.devin.write.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MetaIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(log: MetaIndexTable): Long

    @Query("DELETE FROM ${MetaIndexTable.TABLE_NAME}")
    fun nukeTable()

}