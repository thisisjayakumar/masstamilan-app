package com.masstamilan.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [Index("songKey", unique = true)]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songKey: String,
    val songId: Int = 0,
    val name: String = "",
    val artists: String = "",
    val movieName: String = "",
    val movieSlug: String = "",
    val songPagePath: String = "",
    val imageName: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun key(movieSlug: String, songPagePath: String, songId: Int) =
            "$movieSlug|$songPagePath|$songId"
    }
}
