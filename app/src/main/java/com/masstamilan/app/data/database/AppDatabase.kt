package com.masstamilan.app.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.masstamilan.app.data.dao.DownloadDao
import com.masstamilan.app.data.dao.FavoriteDao
import com.masstamilan.app.data.dao.PlaylistDao
import com.masstamilan.app.data.entity.FavoriteEntity
import com.masstamilan.app.data.entity.PlaylistEntity
import com.masstamilan.app.data.entity.PlaylistSongEntity
import com.masstamilan.app.data.model.DownloadEntity

@Database(
    entities = [
        DownloadEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
                        override fun migrate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS favorites (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "songKey TEXT NOT NULL UNIQUE," +
                                    "songId INTEGER NOT NULL," +
                                    "name TEXT NOT NULL," +
                                    "artists TEXT NOT NULL," +
                                    "movieName TEXT NOT NULL," +
                                    "movieSlug TEXT NOT NULL," +
                                    "songPagePath TEXT NOT NULL," +
                                    "imageName TEXT NOT NULL," +
                                    "streamUrl TEXT NOT NULL DEFAULT ''," +
                                    "addedAt INTEGER NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS playlists (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "name TEXT NOT NULL," +
                                    "createdAt INTEGER NOT NULL," +
                                    "updatedAt INTEGER NOT NULL)"
                            )
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS playlist_songs (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                    "playlistId INTEGER NOT NULL," +
                                    "position INTEGER NOT NULL," +
                                    "songKey TEXT NOT NULL," +
                                    "songId INTEGER NOT NULL," +
                                    "name TEXT NOT NULL," +
                                    "artists TEXT NOT NULL," +
                                    "movieName TEXT NOT NULL," +
                                    "movieSlug TEXT NOT NULL," +
                                    "songPagePath TEXT NOT NULL," +
                                    "streamUrl TEXT NOT NULL DEFAULT ''," +
                                    "imageName TEXT NOT NULL)"
                            )
                        }
                    }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN streamUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE playlist_songs ADD COLUMN streamUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "masstamilan_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
