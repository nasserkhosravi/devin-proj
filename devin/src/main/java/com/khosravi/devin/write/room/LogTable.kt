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
    @ColumnInfo(index = true, name = COLUMN_TAG)
    val tag: String,
    @ColumnInfo(name = COLUMN_VALUE)
    val value: String,
    @ColumnInfo(name = COLUMN_DATE)
    val date: Long,
    @ColumnInfo(name = COLUMN_META)
    val meta: String?,
    @ColumnInfo(index = true, name = COLUMN_CLIENT_ID)
    val clientId: String,
) {

    companion object {
        const val TABLE_NAME = "log"

        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_TAG = "_tag"
        const val COLUMN_VALUE = "_value"
        const val COLUMN_DATE = "_date"
        const val COLUMN_META = "_meta"
        const val COLUMN_CLIENT_ID = "_clientId"
    }
}