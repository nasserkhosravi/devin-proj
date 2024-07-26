package com.khosravi.devin.write.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LogTable::class, ClientTable::class], version = 2)
abstract class DevinDB : RoomDatabase() {

    abstract fun logDao(): LogDao
    abstract fun clientDao(): ClientDao

    companion object {
        private var sInstance: DevinDB? = null
        private const val DB_NAME = "db_devin"

        @Synchronized
        fun getInstance(context: Context): DevinDB {
            if (sInstance == null) {

                sInstance = Room.databaseBuilder(context.applicationContext, DevinDB::class.java, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
            }
            return sInstance!!
        }
    }
}