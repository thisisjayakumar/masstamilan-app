package com.masstamilan.app.data.dao

import androidx.room.*
import com.masstamilan.app.data.entity.PlaylistEntity
import com.masstamilan.app.data.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :pid ORDER BY position")
    fun observeSongs(pid: Long): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(p: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :pid")
    suspend fun clearSongs(pid: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(s: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE id = :sid")
    suspend fun removeSong(sid: Long)

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): PlaylistEntity?

    @Query("UPDATE playlist_songs SET songKey = :newKey, songPagePath = :pagePath WHERE songKey = :oldKey")
    suspend fun rekeySongs(oldKey: String, newKey: String, pagePath: String)

    @Query("UPDATE playlist_songs SET position = :pos WHERE id = :sid")
    suspend fun setSongPosition(sid: Long, pos: Int)
}
