package com.masstamilan.app.data.database

import android.content.Context
import androidx.room.*
import com.masstamilan.app.data.dao.DownloadDao
import com.masstamilan.app.data.model.DownloadEntity

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "masstamilan_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
