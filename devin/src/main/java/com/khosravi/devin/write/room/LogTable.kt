package com.khosravi.devin.write.room

import android.provider.BaseColumns
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = LogTable.TABLE_NAME)
class LogTable(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(index = true, name = COLUMN_ID)
    val id: Long,
    @ColumnInfo(index = true, name = COLUMN_TYPE)
    val type: String,
    @ColumnInfo(index = true, name = COLUMN_VALUE)
    val value: String,
    @ColumnInfo(index = true, name = COLUMN_DATE)
    val date: Long,
) {

    companion object {
        const val TABLE_NAME = "log"

        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_TYPE = "_type"
        const val COLUMN_VALUE = "_value"
        const val COLUMN_DATE = "_date"

    }
}