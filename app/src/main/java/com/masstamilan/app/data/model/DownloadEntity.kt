package com.masstamilan.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Int = 0,
    val songName: String = "",
    val artist: String = "",
    val movieName: String = "",
    val downloadUrl: String = "",
    val quality: String = "128kbps",
    val filePath: String = "",
    val status: String = "pending", // pending, downloading, completed, failed
    val progress: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)
