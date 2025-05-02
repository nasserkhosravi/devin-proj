package com.khosravi.devin.write.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import com.khosravi.devin.read.DevinPersistenceFlagsApi

@Entity(tableName = LogTable.TABLE_NAME)
class LogTable(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(index = true, name = COLUMN_ID)
    val id: Long,
    @ColumnInfo(index = true, name = COLUMN_TAG)
    val tag: String,
    @ColumnInfo(name = COLUMN_VALUE)
    val value: String,
    @ColumnInfo(index = true, name = COLUMN_DATE)
    val date: Long,
    @ColumnInfo(name = COLUMN_META)
    val meta: String?,
    @ColumnInfo(index = true, name = COLUMN_CLIENT_ID)
    val clientId: String,
    @ColumnInfo(index = true, name = COLUMN_TYPE_ID)
    val typeId: String?
) {

    companion object {
        const val TABLE_NAME = "log"

        const val COLUMN_ID = DevinPersistenceFlagsApi.KEY_ID
        const val COLUMN_TAG = DevinPersistenceFlagsApi.KEY_TAG
        const val COLUMN_VALUE = DevinPersistenceFlagsApi.KEY_VALUE
        const val COLUMN_DATE = DevinPersistenceFlagsApi.KEY_DATE
        const val COLUMN_META = DevinPersistenceFlagsApi.KEY_META
        const val COLUMN_CLIENT_ID = DevinPersistenceFlagsApi.KEY_CLIENT_ID
        const val COLUMN_TYPE_ID = DevinPersistenceFlagsApi.KEY_TYPE_ID
    }
}