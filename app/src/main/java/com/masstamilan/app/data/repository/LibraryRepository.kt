package com.masstamilan.app.data.repository

import com.masstamilan.app.core.media.TrackRefresher
import com.masstamilan.app.data.dao.FavoriteDao
import com.masstamilan.app.data.dao.PlaylistDao
import com.masstamilan.app.data.entity.FavoriteEntity
import com.masstamilan.app.data.entity.PlaylistEntity
import com.masstamilan.app.data.entity.PlaylistSongEntity
import com.masstamilan.app.data.model.QueueTrack
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.model.songPagePathOf
import com.masstamilan.app.data.remote.MasstamilanApi
import com.masstamilan.app.data.remote.MasstamilanParsers
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

    /** Insert-only favorite add used by backup import (no toggle semantics). */
    suspend fun addFavorite(track: QueueTrack) {
        val key = favoriteKey(track)
        if (!favoriteDao.isFavorite(key)) {
            favoriteDao.insert(toEntity(track, key))
        }
    }

    suspend fun allFavoritesList(): List<FavoriteEntity> = favorites().first()

    suspend fun allPlaylistsList(): List<PlaylistEntity> = playlists().first()

    suspend fun playlistByName(name: String): PlaylistEntity? = playlistDao.getByName(name)

    /**
     * Persist a freshly re-resolved song-page path back to every library row
     * sharing the track's key, re-keying to the canonical key so future
     * like/unlike lookups keep matching. No-op when already canonical.
     */
    suspend fun rekeyWithPagePath(track: QueueTrack, pagePath: String) {
        val oldKey = favoriteKey(track)
        val newKey = FavoriteEntity.key(track.movieSlug, pagePath, track.songId)
        if (oldKey == newKey) return
        favoriteDao.rekey(oldKey, newKey, pagePath)
        playlistDao.rekeySongs(oldKey, newKey, pagePath)
    }

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

/**
 * Pure match step of library refresh: pick the movie-page song for a stale
 * library track, preferring stable song id over title. Unit-tested.
 */
fun selectRefreshMatch(songs: List<SongResult>, track: QueueTrack): SongResult? {
    if (songs.isEmpty()) return null
    if (track.songId != 0) {
        songs.firstOrNull { it.id == track.songId }?.let { return it }
    }
    val want = MasstamilanParsers.normalizeSongName(track.title)
    if (want.isNotBlank()) {
        songs.firstOrNull { MasstamilanParsers.normalizeSongName(it.name) == want }?.let { return it }
    }
    return null
}

/**
 * One-time enricher for pre-pagePath library rows: re-fetches the movie page
 * at click time, matches the song, persists the stable page path back to the
 * library (re-keyed) and returns the enriched track for lively resolution.
 * Signed stream URLs are never persisted — they expire within a day.
 */
@Singleton
class LibraryTrackRefresher @Inject constructor(
    private val api: MasstamilanApi,
    private val library: LibraryRepository
) : TrackRefresher {
    override suspend fun refresh(track: QueueTrack): QueueTrack? {
        if (track.songPagePath.isNotBlank() || track.movieSlug.isBlank()) return null
        val songs = try {
            api.getSongsFromMovieSlug(track.movieSlug)
        } catch (_: Exception) {
            return null
        }
        val match = selectRefreshMatch(songs, track) ?: return null
        val pagePath = songPagePathOf(match)
        if (pagePath.isBlank()) return null
        try {
            library.rekeyWithPagePath(track, pagePath)
        } catch (_: Exception) {
            // Enrichment still succeeds for this playback even if persist fails.
        }
        return track.copy(songPagePath = pagePath)
    }
}
