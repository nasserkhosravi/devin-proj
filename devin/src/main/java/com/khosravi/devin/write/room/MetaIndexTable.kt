package com.khosravi.devin.write.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = MetaIndexTable.TABLE_NAME,
    primaryKeys = [MetaIndexTable.COLUMN_ID, MetaIndexTable.COLUMN_FIELD_NAME],
    foreignKeys = [ForeignKey(
        entity = LogTable::class, onDelete = ForeignKey.CASCADE,
        parentColumns = [LogTable.COLUMN_ID],
        childColumns = [MetaIndexTable.COLUMN_ID]
    )]
)
data class MetaIndexTable(
    @ColumnInfo(index = true, name = COLUMN_ID) val logId: Long,
    @ColumnInfo(index = true, name = COLUMN_FIELD_NAME) val fieldName: String,  // e.g. "url", "statusCode"
    @ColumnInfo(name = COLUMN_FIELD_VALUE) val fieldValue: String
) {

    companion object {
        const val TABLE_NAME = "meta_index"
        const val COLUMN_ID = "log_id"
        const val COLUMN_FIELD_NAME = "field"
        const val COLUMN_FIELD_VALUE = "value"
    }
}