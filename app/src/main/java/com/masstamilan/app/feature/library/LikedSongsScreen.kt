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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.masstamilan.app.ui.theme.Primary
import com.masstamilan.app.ui.theme.Surface
import com.masstamilan.app.ui.theme.TextHint
import com.masstamilan.app.ui.theme.TextPrimary
import com.masstamilan.app.ui.theme.TextSecondary
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

/** Dedicated Liked Songs screen: full list with Play-all, Shuffle, tap-to-play, unlike. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playbackManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, AppEntryPoint::class.java
        ).playbackManager()
    }
    val favorites by viewModel.favorites.collectAsState()
    val managerError by playbackManager.playbackErrorFlow.collectAsState()
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(managerError) {
        if (managerError != null) toast = managerError
    }
    toast?.let {
        LaunchedEffect(it) {
            delay(2500)
            toast = null
            playbackManager.clearPlaybackError()
        }
    }

    fun playAt(index: Int, shuffle: Boolean) {
        if (favorites.isEmpty()) return
        val queue = favorites.map { it.toQueueTrack() }
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
                title = { Text("Liked Songs • ${favorites.size}", color = TextPrimary) },
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
            if (favorites.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { playAt(0, shuffle = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = TextPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Play", color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = { playAt(0, shuffle = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shuffle, null, tint = Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle", color = TextPrimary)
                    }
                }
            }
            if (favorites.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tap the heart on any song to save it here.", color = TextHint)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(favorites, key = { it.songKey }) { fav ->
                        val art = fav.imageName.ifBlank { null }
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable {
                                    val idx = favorites.indexOfFirst { it.songKey == fav.songKey }
                                    playAt(idx.coerceAtLeast(0), shuffle = false)
                                },
                            colors = CardDefaults.cardColors(containerColor = CardColor)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (art != null) {
                                    AsyncImage(model = art, contentDescription = null, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fav.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(fav.artists, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                                }
                                IconButton(onClick = { viewModel.toggleFavorite(fav.toQueueTrack()) }) {
                                    Icon(Icons.Default.Favorite, "Unlike", tint = Primary)
                                }
                            }
                        }
                    }
                }
            }
            toast?.let {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(it, color = TextSecondary, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}
