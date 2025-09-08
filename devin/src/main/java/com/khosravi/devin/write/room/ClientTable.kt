package com.khosravi.devin.write.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.khosravi.devin.read.DevinPersistenceFlagsApi

@Entity(tableName = ClientTable.TABLE_NAME)
class ClientTable(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(index = true, name = COLUMN_PACKAGE_ID)
    val packageId: String,

    @ColumnInfo(name = COLUMN_PRESENTER_CONFIG)
    val presenterConfig: String?
) {

    companion object {
        const val TABLE_NAME = "client"

        const val COLUMN_PACKAGE_ID = DevinPersistenceFlagsApi.KEY_CLIENT_ID
        const val COLUMN_PRESENTER_CONFIG = "_presenterConfig"
    }
}