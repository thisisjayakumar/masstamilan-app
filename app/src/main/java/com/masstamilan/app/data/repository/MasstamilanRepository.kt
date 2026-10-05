package com.masstamilan.app.data.repository

import com.masstamilan.app.core.util.StringMatcher
import com.masstamilan.app.data.database.AppDatabase
import com.masstamilan.app.data.model.AutocompleteSuggestion
import com.masstamilan.app.data.model.DownloadEntity
import com.masstamilan.app.data.model.RankedSong
import com.masstamilan.app.data.model.SearchResult
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.model.pagePathOf
import com.masstamilan.app.data.remote.MasstamilanApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasstamilanRepository @Inject constructor(
    private val api: MasstamilanApi,
    private val database: AppDatabase
) {
    // ---- Movie-level (legacy) ----
    suspend fun searchMovies(keyword: String): List<SearchResult> =
        api.searchMovies(keyword)

    /** Kept for old call sites. */
    suspend fun searchSongs(keyword: String): List<SearchResult> = searchMovies(keyword)

    suspend fun getMovieSongs(movieSlug: String): List<SongResult> =
        api.getSongsFromMovieSlug(movieSlug)

    suspend fun getDownloadUrl(songPageHtml: String, quality: String = "320kbps"): String? =
        api.getDownloadUrl(songPageHtml, quality)

    suspend fun resolveStreamUrl(songPath: String, prefer320: Boolean = true): String? =
        api.resolveStreamUrl(songPath, prefer320)

    suspend fun autocomplete(keyword: String): List<AutocompleteSuggestion> =
        api.autocomplete(keyword)

    // ---- Unified song-level search (Spotify-like) ----
    /**
     * 1. autocomplete (/search/ac) for instant suggestions
     * 2. movie search (/search) → top [maxMovies] movie pages
     * 3. fan-out fetch tracks in parallel → fuzzy rank with [StringMatcher]
     *
     * Returns ranked songs (score desc). Empty query → empty list.
     */
    suspend fun unifiedSongSearch(
        keyword: String,
        maxMovies: Int = 5,
        maxResults: Int = 30
    ): List<RankedSong> = coroutineScope {
        val q = keyword.trim()
        if (q.length < 2) return@coroutineScope emptyList()

        val movies = try {
            api.searchMovies(q).take(maxMovies)
        } catch (_: Exception) {
            emptyList()
        }
        if (movies.isEmpty()) return@coroutineScope emptyList()

        val perMovie: List<List<SongResult>> = movies.map { movie ->
            async {
                try {
                    api.getSongsFromMovieSlug(movie.slug).map { song ->
                        // Backfill movie name from card when page parse misses it
                        if (song.movieName.isBlank()) song.copy(movieName = movie.name)
                        else song
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll()

        val all = perMovie.flatten()
        if (all.isEmpty()) return@coroutineScope emptyList()

        StringMatcher.rankSongs(q, dedupeSongs(all))
            .take(maxResults)
            .map { (song, score) ->
                RankedSong(
                    song = song,
                    score = score,
                    movieSlug = slugFor(song)
                )
            }
    }

    private fun slugFor(song: SongResult): String = pagePathOf(song.dlPath)

    // ---- Downloads ----
    fun getDownloads(): Flow<List<DownloadEntity>> =
        database.downloadDao().getAllDownloads()

    suspend fun insertDownload(download: DownloadEntity): Long {
        return database.downloadDao().insertDownload(download)
    }

    suspend fun updateProgress(id: Long, status: String, progress: Float, downloaded: Long, total: Long) {
        database.downloadDao().updateProgress(id, status, progress, downloaded, total)
    }

    suspend fun markCompleted(id: Long, filePath: String) {
        database.downloadDao().markCompleted(id, "completed", filePath)
    }

    suspend fun markFailed(id: Long) {
        database.downloadDao().updateProgress(id, "failed", 0f, 0L, 0L)
    }

    suspend fun updateDownload(download: DownloadEntity) {
        database.downloadDao().updateDownload(download)
    }

    suspend fun deleteDownload(id: Long) {
        database.downloadDao().deleteDownloadById(id)
    }
}

/**
 * Pure pipeline step of unified search: deduplicate scraped tracks by
 * song-page path (dlPath), keeping first occurrence. Unit-tested.
 */
fun dedupeSongs(songs: List<SongResult>): List<SongResult> {
    val seen = LinkedHashSet<String>()
    return songs.filter { s ->
        seen.add(s.dlPath.ifBlank { "${s.movieName}|${s.name}" })
    }
}
