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
        imageName = t.artwork, streamUrl = t.streamUrl.orEmpty()
    )

    // ---- Playlists ----
    suspend fun createPlaylist(name: String): Long = playlistDao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun getPlaylist(id: Long): PlaylistEntity? = playlistDao.get(id)

    suspend fun renamePlaylist(id: Long, name: String) {
        playlistDao.updatePlaylist(playlistDao.get(id)?.copy(name = name, updatedAt = System.currentTimeMillis()) ?: return)
    }

    suspend fun deletePlaylist(id: Long) {
        playlistDao.clearSongs(id); playlistDao.deletePlaylist(id)
    }

    suspend fun addSongToPlaylist(pid: Long, t: QueueTrack) {
        val position = playlistDao.observeSongs(pid).first().size
        playlistDao.insertSong(PlaylistSongEntity(
            playlistId = pid, position = position, songKey = favoriteKey(t),
            songId = t.songId, name = t.title, artists = t.artist,
            movieName = t.title, movieSlug = t.movieSlug,
            songPagePath = t.songPagePath, imageName = t.artwork,
            streamUrl = t.streamUrl.orEmpty()
        ))
        playlistDao.get(pid)?.let { playlistDao.updatePlaylist(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    suspend fun removeSong(sid: Long) = playlistDao.removeSong(sid)

    suspend fun moveSong(sid: Long, pos: Int) = playlistDao.setSongPosition(sid, pos)

    suspend fun playlistSongsList(pid: Long): List<PlaylistSongEntity> =
        playlistDao.observeSongs(pid).first()
}

/**
 * Pure mappers: stored library rows → playable queue entries.
 * Song-page paths resolve lazily via StreamResolver; unit-tested.
 */
fun PlaylistSongEntity.toQueueTrack(): QueueTrack = QueueTrack(
    streamUrl = streamUrl.ifBlank { null },
    title = name,
    artist = artists,
    artwork = imageName,
    songPagePath = songPagePath,
    movieSlug = movieSlug,
    songId = songId
)

fun FavoriteEntity.toQueueTrack(): QueueTrack = QueueTrack(
    streamUrl = streamUrl.ifBlank { null },
    title = name,
    artist = artists,
    artwork = imageName,
    songPagePath = songPagePath,
    movieSlug = movieSlug,
    songId = songId
)

/** Build a QueueTrack for library actions from a scraped song + its page path. */
fun queueTrackFor(
    songId: Int,
    title: String,
    artist: String,
    artwork: String,
    songPagePath: String,
    movieSlug: String,
    streamUrl: String? = null
): QueueTrack = QueueTrack(
    streamUrl = streamUrl,
    title = title,
    artist = artist,
    artwork = artwork,
    songPagePath = songPagePath,
    movieSlug = movieSlug,
    songId = songId
)

/** Direct stream URL for a scraped dl_path (already-signed /downloader/ or http link). */
fun directStreamUrl(dlPath: String): String? {
    val dl = dlPath.trim()
    return when {
        dl.startsWith("/downloader/") -> com.masstamilan.app.data.remote.MasstamilanApi.BASE_URL + dl
        dl.startsWith("http") -> dl
        else -> null
    }
}

/**
 * Press-time shuffle: returns shuffled queue + new start index pointing at the
 * originally-selected track. Pure + unit-tested.
 */
fun shuffledQueueWithStart(
    tracks: List<QueueTrack>,
    startIndex: Int,
    random: java.util.Random = java.util.Random()
): Pair<List<QueueTrack>, Int> {
    if (tracks.isEmpty()) return emptyList<QueueTrack>() to 0
    val shuffled = tracks.toMutableList().apply { java.util.Collections.shuffle(this, random) }
    val selected = tracks.getOrNull(startIndex.coerceIn(tracks.indices))
    val newIndex = if (selected != null) shuffled.indexOf(selected).takeIf { it >= 0 } ?: 0 else 0
    return shuffled to newIndex
}
