package com.masstamilan.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masstamilan.app.data.entity.FavoriteEntity
import com.masstamilan.app.data.entity.PlaylistEntity
import com.masstamilan.app.data.entity.PlaylistSongEntity
import com.masstamilan.app.data.model.QueueTrack
import com.masstamilan.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteEntity>> = library.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = library.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun songs(pid: Long): Flow<List<PlaylistSongEntity>> = library.playlistSongs(pid)

    fun createPlaylist(name: String, onDone: (Long) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { onDone(library.createPlaylist(trimmed)) }
    }

    fun renamePlaylist(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { library.renamePlaylist(id, trimmed) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { library.deletePlaylist(id) }
    }

    fun removeSongFromPlaylist(sid: Long) {
        viewModelScope.launch { library.removeSong(sid) }
    }

    fun toggleFavorite(track: QueueTrack, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onDone(library.toggleFavorite(track)) }
    }

    suspend fun playlistName(pid: Long): String =
        library.getPlaylist(pid)?.name ?: "Playlist"
}
