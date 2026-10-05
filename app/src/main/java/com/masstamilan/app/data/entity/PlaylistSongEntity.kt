package com.masstamilan.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_songs",
    indices = [Index("playlistId"), Index("position")]
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val position: Int = 0,
    val songKey: String = "",
    val songId: Int = 0,
    val name: String = "",
    val artists: String = "",
    val movieName: String = "",
    val movieSlug: String = "",
    val songPagePath: String = "",
    val imageName: String = ""
)
