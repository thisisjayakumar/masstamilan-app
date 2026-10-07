package com.masstamilan.app.feature.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.masstamilan.app.core.di.AppEntryPoint
import com.masstamilan.app.core.util.Artwork
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.model.songPagePathOf
import com.masstamilan.app.data.repository.LibraryRepository
import com.masstamilan.app.data.repository.directStreamUrl
import com.masstamilan.app.data.repository.queueTrackFor
import com.masstamilan.app.ui.theme.Primary
import com.masstamilan.app.ui.theme.TextPrimary
import com.masstamilan.app.ui.theme.TextSecondary
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * Shared ⋮ overflow menu for any song row: play, like, add-to-playlist,
 * download, view album. Self-contained: resolves Hilt singletons itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: SongResult,
    movieSlug: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onAlbum: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library: LibraryRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, AppEntryPoint::class.java
        ).libraryRepository()
    }
    val track = remember(song, movieSlug) {
        queueTrackFor(
            songId = song.id,
            title = song.name,
            artist = song.artists,
            artwork = Artwork.url(song.imageName),
            songPagePath = songPagePathOf(song),
            movieSlug = movieSlug.ifBlank { song.movieName },
            streamUrl = directStreamUrl(song.dlPath)
        )
    }
    var isFav by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    val playlists by library.playlists().collectAsState(initial = emptyList())

    LaunchedEffect(track) {
        isFav = library.isFavorite(track)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val art = Artwork.url(song.imageName).ifBlank { null }
                if (art != null) {
                    AsyncImage(model = art, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        listOf(song.artists, song.movieName).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            SheetRow(icon = Icons.Default.PlayArrow, label = "Play") {
                onDismiss(); onPlay()
            }
            SheetRow(
                icon = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (isFav) "Unlike" else "Like"
            ) {
                scope.launch {
                    isFav = library.toggleFavorite(track)
                    toast = if (isFav) "Added to Liked Songs" else "Removed from Liked Songs"
                }
            }
            SheetRow(icon = Icons.Default.PlaylistAdd, label = "Add to playlist") {
                showPlaylists = !showPlaylists
            }
            if (showPlaylists) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New playlist…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (newName.isBlank()) return@TextButton
                            scope.launch {
                                val pid = library.createPlaylist(newName.trim())
                                library.addSongToPlaylist(pid, track)
                                toast = "Added to \"${newName.trim()}\""
                                newName = ""
                                showPlaylists = false
                            }
                        }
                    ) { Text("Create") }
                }
                if (playlists.isEmpty()) {
                    Text("No playlists yet — create one above.", color = TextSecondary)
                } else {
                    LazyColumn(modifier = Modifier.height(160.dp)) {
                        items(playlists, key = { it.id }) { pl ->
                            Text(
                                text = pl.name,
                                color = TextPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            library.addSongToPlaylist(pl.id, track)
                                            toast = "Added to \"${pl.name}\""
                                            showPlaylists = false
                                        }
                                    }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
            SheetRow(icon = Icons.Default.Download, label = "Download") {
                onDismiss(); onDownload()
            }
            SheetRow(icon = Icons.Default.Album, label = "View album") {
                onDismiss(); onAlbum()
            }
            toast?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}
