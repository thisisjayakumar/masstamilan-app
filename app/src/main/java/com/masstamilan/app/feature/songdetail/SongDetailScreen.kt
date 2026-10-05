package com.masstamilan.app.feature.songdetail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.masstamilan.app.core.di.AppEntryPoint
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.model.toQueue
import com.masstamilan.app.data.remote.MasstamilanApi
import com.masstamilan.app.feature.common.SongActionsSheet
import com.masstamilan.app.ui.theme.*
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(navController: NavController, movieSlug: String, api: MasstamilanApi? = null) {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<SongResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var playingIndex by remember { mutableIntStateOf(-1) }
    var toast by remember { mutableStateOf<String?>(null) }
    var menuSong by remember { mutableStateOf<SongResult?>(null) }

    // NavHost passes no api/helper; resolve Hilt singletons instead.
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, AppEntryPoint::class.java
        )
    }
    val resolvedApi = api ?: remember { entryPoint.api() }
    val playbackManager = remember { entryPoint.playbackManager() }

    LaunchedEffect(movieSlug) {
        try {
            val html = resolvedApi.getMoviePage(movieSlug)
            songs = resolvedApi.getSongsFromMoviePage(html)
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading songs", Toast.LENGTH_SHORT).show()
        } finally {
            loading = false
        }
    }

    fun playSong(song: SongResult) {
        // Whole album becomes the queue; the manager resolves each URL lazily.
        val queue = songs.toQueue(movieSlug)
        val index = queue.indexOfFirst { it.songId == song.id }.takeIf { it >= 0 } ?: 0
        if (playbackManager.playQueue(context, queue, index)) {
            playingIndex = song.id
            navController.navigate("player/${song.id}")
        } else {
            toast = "Couldn't resolve stream for \"${song.name}\""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(movieSlug.replace("-", " ").replaceFirstChar { it.uppercase() }, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(songs) { song ->
                        SongListItem(
                            song = song,
                            playingIndex = playingIndex,
                            onPlay = { playSong(song) },
                            onDownload = { navController.navigate("downloads") },
                            onMore = { menuSong = song }
                        )
                    }
                }
            }
            toast?.let {
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(2500)
                    toast = null
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(it, color = TextSecondary, modifier = Modifier.padding(8.dp))
                }
            }
            menuSong?.let { song ->
                SongActionsSheet(
                    song = song,
                    movieSlug = movieSlug,
                    onDismiss = { menuSong = null },
                    onPlay = { playSong(song) },
                    onDownload = { navController.navigate("downloads") },
                    onAlbum = { /* already on the album */ }
                )
            }
        }
    }
}

@Composable
fun SongListItem(
    song: SongResult,
    playingIndex: Int,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onMore: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(song.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(song.artists, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                val meta = listOfNotNull(
                    song.duration.ifBlank { null },
                    song.downloads.takeIf { it > 0 }?.let { "$it downloads" }
                ).joinToString(" | ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = TextHint)
                }
            }
            Row {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Primary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, "Download", tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Default.MoreVert, "More options", tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
