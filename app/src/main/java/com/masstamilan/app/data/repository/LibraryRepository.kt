package com.masstamilan.app.data.repository

import com.masstamilan.app.data.dao.FavoriteDao
import com.masstamilan.app.data.dao.PlaylistDao
import com.masstamilan.app.data.entity.FavoriteEntity
import com.masstamilan.app.data.entity.PlaylistEntity
import com.masstamilan.app.data.entity.PlaylistSongEntity
import com.masstamilan.app.data.model.QueueTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class LibraryStats(
    val favorites: Int = 0,
    val playlists: Int = 0
)

@Singleton
class LibraryRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao
) {
    fun favorites(): Flow<List<FavoriteEntity>> = favoriteDao.observeAll()
    fun playlists(): Flow<List<PlaylistEntity>> = playlistDao.observeAll()
    fun playlistSongs(pid: Long): Flow<List<PlaylistSongEntity>> = playlistDao.observeSongs(pid)
    suspend fun stats() = LibraryStats(favoriteDao.count(), 0)

    fun favoriteKey(t: QueueTrack) = FavoriteEntity.key(t.movieSlug, t.songPagePath, t.songId)

    suspend fun toggleFavorite(track: QueueTrack): Boolean {
        val key = favoriteKey(track)
        return if (favoriteDao.isFavorite(key)) {
            favoriteDao.deleteByKey(key); false
        } else {
            favoriteDao.insert(toEntity(track, key)); true
        }
    }

    suspend fun isFavorite(track: QueueTrack) = favoriteDao.isFavorite(favoriteKey(track))

    private fun toEntity(t: QueueTrack, key: String) = FavoriteEntity(
        songKey = key, songId = t.songId, name = t.title, artists = t.artist,
        movieName = t.title, movieSlug = t.movieSlug, songPagePath = t.songPagePath,
        imageName = t.artwork
    )

    // ---- Playlists ----
    suspend fun createPlaylist(name: String): Long = playlistDao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun renamePlaylist(id: Long, name: String) {
        playlistDao.updatePlaylist(playlistDao.get(id)?.copy(name = name, updatedAt = System.currentTimeMillis()) ?: return)
    }

    suspend fun deletePlaylist(id: Long) {
        playlistDao.clearSongs(id); playlistDao.deletePlaylist(id)
    }

    suspend fun addSongToPlaylist(pid: Long, t: QueueTrack, position: Int) {
        playlistDao.insertSong(PlaylistSongEntity(
            playlistId = pid, position = position, songKey = favoriteKey(t),
            songId = t.songId, name = t.title, artists = t.artist,
            movieName = t.title, movieSlug = t.movieSlug,
            songPagePath = t.songPagePath, imageName = t.artwork
        ))
        playlistDao.get(pid)?.let { playlistDao.updatePlaylist(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    suspend fun removeSong(sid: Long) = playlistDao.removeSong(sid)

    suspend fun moveSong(sid: Long, pos: Int) = playlistDao.setSongPosition(sid, pos)

    suspend fun playlistSongsList(pid: Long): List<PlaylistSongEntity> =
        playlistDao.observeSongs(pid).first()
}
