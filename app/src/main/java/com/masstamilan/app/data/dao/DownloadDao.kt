package com.masstamilan.app.data.dao

import androidx.room.*
import com.masstamilan.app.data.model.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('pending', 'downloading') ORDER BY addedAt DESC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Query("UPDATE downloads SET status = :status, progress = :progress, downloadedBytes = :downloaded, totalBytes = :total WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, progress: Float, downloaded: Long, total: Long)

    @Query("UPDATE downloads SET status = :status, filePath = :filePath, progress = 1.0 WHERE id = :id")
    suspend fun markCompleted(id: Long, status: String, filePath: String)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Delete
    suspend fun deleteDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)
}
