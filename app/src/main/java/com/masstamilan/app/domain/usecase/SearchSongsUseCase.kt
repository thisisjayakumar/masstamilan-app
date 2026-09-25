package com.masstamilan.app.domain.usecase

import com.masstamilan.app.data.model.AutocompleteSuggestion
import com.masstamilan.app.data.model.RankedSong
import com.masstamilan.app.data.model.SearchResult
import com.masstamilan.app.data.repository.MasstamilanRepository
import javax.inject.Inject

/**
 * Unified song-level search: debounced in ViewModel, ranked here.
 */
class SearchSongsUseCase @Inject constructor(
    private val repository: MasstamilanRepository
) {
    /** Ranked song results (primary, Spotify-like). */
    suspend operator fun invoke(
        keyword: String,
        maxMovies: Int = 5,
        maxResults: Int = 30
    ): List<RankedSong> = repository.unifiedSongSearch(keyword, maxMovies, maxResults)

    suspend fun autocomplete(keyword: String): List<AutocompleteSuggestion> =
        repository.autocomplete(keyword)

    suspend fun searchMovies(keyword: String): List<SearchResult> =
        repository.searchMovies(keyword)
}
