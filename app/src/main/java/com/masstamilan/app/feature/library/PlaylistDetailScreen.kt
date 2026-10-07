package com.masstamilan.app.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.masstamilan.app.core.di.AppEntryPoint
import com.masstamilan.app.data.repository.shuffledQueueWithStart
import com.masstamilan.app.data.repository.toQueueTrack
import com.masstamilan.app.ui.theme.Card as CardColor
import com.masstamilan.app.ui.theme.Error
import com.masstamilan.app.ui.theme.Primary
import com.masstamilan.app.ui.theme.Surface
import com.masstamilan.app.ui.theme.TextHint
import com.masstamilan.app.ui.theme.TextPrimary
import com.masstamilan.app.ui.theme.TextSecondary
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavController,
    playlistId: Long,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playbackManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, AppEntryPoint::class.java
        ).playbackManager()
    }
    val songs by viewModel.songs(playlistId).collectAsState(initial = null)
    var name by remember { mutableStateOf("Playlist") }
    var toast by remember { mutableStateOf<String?>(null) }
    val managerError by playbackManager.playbackErrorFlow.collectAsState()
    LaunchedEffect(managerError) {
        if (managerError != null) toast = managerError
    }

    LaunchedEffect(playlistId) {
        name = viewModel.playlistName(playlistId)
    }

    fun playAt(list: List<com.masstamilan.app.data.entity.PlaylistSongEntity>, index: Int, shuffle: Boolean) {
        if (list.isEmpty()) return
        val queue = list.map { it.toQueueTrack() }
        if (shuffle) {
            val (shuffled, newIndex) = shuffledQueueWithStart(queue, index)
            if (playbackManager.playQueue(context, shuffled, newIndex)) {
                navController.navigate("player/${shuffled[newIndex].songId}")
            } else {
                toast = "Couldn't play this track — re-add it from Search"
            }
        } else {
            if (playbackManager.playQueue(context, queue, index)) {
                navController.navigate("player/${queue[index].songId}")
            } else {
                toast = "Couldn't play this track — re-add it from Search"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        val list = songs
        when {
            list == null -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            list.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Empty playlist — add songs from any ⋮ menu.", color = TextHint)
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { playAt(list, 0, shuffle = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Play", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = { playAt(list, 0, shuffle = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Shuffle, null, tint = Primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Shuffle", color = TextPrimary)
                        }
                    }
                }
                items(list, key = { it.id }) { row ->
                    val queue = remember(list) { list.map { it.toQueueTrack() } }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable {
                                val idx = queue.indexOfFirst { it.songId == row.songId }
                                playAt(list, idx.coerceAtLeast(0), shuffle = false)
                            },
                        colors = CardDefaults.cardColors(containerColor = CardColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (row.imageName.isNotBlank()) {
                                AsyncImage(model = row.imageName, contentDescription = null, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(row.artists, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                            }
                            IconButton(onClick = { viewModel.removeSongFromPlaylist(row.id) }) {
                                Icon(Icons.Default.Delete, "Remove", tint = Error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                toast?.let {
                    item {
                        LaunchedEffect(it) {
                            kotlinx.coroutines.delay(2500)
                            toast = null
                            playbackManager.clearPlaybackError()
                        }
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(it, color = TextSecondary, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}
