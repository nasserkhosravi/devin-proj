package com.khosravi.devin.write.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = ClientTable.TABLE_NAME)
class ClientTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(index = true, name = COLUMN_PACKAGE_ID)
    val packageId: String,
) {

    companion object {
        const val TABLE_NAME = "client"

        const val COLUMN_PACKAGE_ID = "_clientId"
    }
}