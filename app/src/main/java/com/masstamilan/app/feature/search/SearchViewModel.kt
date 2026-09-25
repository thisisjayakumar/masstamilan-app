package com.masstamilan.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masstamilan.app.data.model.AutocompleteSuggestion
import com.masstamilan.app.data.model.RankedSong
import com.masstamilan.app.domain.usecase.SearchSongsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val songs: List<RankedSong> = emptyList(),
    val suggestions: List<AutocompleteSuggestion> = emptyList(),
    val error: String? = null,
    val searchedQuery: String = ""
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchSongs: SearchSongsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    /** Called on every keystroke. Debounces: suggestions @150ms, full search @450ms. */
    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query, error = null)
        suggestJob?.cancel()
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(
                songs = emptyList(), suggestions = emptyList(),
                isSearching = false, searchedQuery = ""
            )
            return
        }
        suggestJob = viewModelScope.launch {
            delay(150)
            try {
                val s = searchSongs.autocomplete(query.trim())
                if (_uiState.value.query == query) {
                    _uiState.value = _uiState.value.copy(suggestions = s.take(8))
                }
            } catch (_: Exception) { /* suggestions are best-effort */ }
        }
        searchJob = viewModelScope.launch {
            delay(450)
            runSearch(query.trim())
        }
    }

    fun retry() {
        val q = _uiState.value.query.trim()
        if (q.length >= 2) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(q) }
        }
    }

    private suspend fun runSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        try {
            val songs = searchSongs(query)
            if (_uiState.value.query.trim() == query || _uiState.value.query.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    songs = songs, isSearching = false,
                    searchedQuery = query,
                    error = if (songs.isEmpty()) "No songs found for \"$query\"" else null
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                error = e.message ?: "Search failed. Check connection and retry."
            )
        }
    }
}
