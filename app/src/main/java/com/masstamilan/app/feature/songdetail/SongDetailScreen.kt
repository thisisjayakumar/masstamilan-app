package com.masstamilan.app.feature.songdetail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.remote.MasstamilanApi
import com.masstamilan.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(navController: NavController, movieSlug: String, api: MasstamilanApi? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf<List<SongResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var playingIndex by remember { mutableIntStateOf(-1) }

    val playbackManager = remember { PlaybackManager() }

    LaunchedEffect(movieSlug) {
        try {
            val html = api?.getMoviePage(movieSlug) ?: ""
            songs = api?.getSongsFromMoviePage(html) ?: emptyList()
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading songs", Toast.LENGTH_SHORT).show()
        } finally {
            loading = false
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
                            navController = navController,
                            playbackManager = playbackManager,
                            onPlayingIndexChange = { playingIndex = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongListItem(
    song: SongResult,
    playingIndex: Int,
    navController: NavController,
    playbackManager: PlaybackManager,
    onPlayingIndexChange: (Int) -> Unit
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
                Text("${song.duration} | ${song.downloads} downloads", style = MaterialTheme.typography.labelSmall, color = TextHint)
            }
            Row {
                IconButton(onClick = {
                    onPlayingIndexChange(if (playingIndex == song.id) { -1 } else song.id)
                    // Play functionality
                }) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Primary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = {
                    navController.navigate("player/${song.id}")
                }) {
                    Icon(Icons.Default.Download, "Download", tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
