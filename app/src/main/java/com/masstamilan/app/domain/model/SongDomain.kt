package com.masstamilan.app.domain.model

data class SongDomain(
    val id: Int = 0,
    val name: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: String = "",
    val imageUrl: String = "",
    val downloadUrl128: String = "",
    val downloadUrl320: String = "",
    val moviePageUrl: String = ""
)

data class DownloadState(
    val id: Long = 0,
    val songName: String = "",
    val status: String = "pending",
    val progress: Float = 0f,
    val filePath: String = ""
)
