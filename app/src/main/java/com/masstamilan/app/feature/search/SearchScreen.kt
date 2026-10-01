package com.masstamilan.app.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.data.model.RankedSong
import com.masstamilan.app.data.repository.MasstamilanRepository
import com.masstamilan.app.ui.theme.Card as CardColor
import com.masstamilan.app.ui.theme.Primary
import com.masstamilan.app.ui.theme.Surface
import com.masstamilan.app.ui.theme.TextHint
import com.masstamilan.app.ui.theme.TextPrimary
import com.masstamilan.app.ui.theme.TextSecondary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SearchEntryPoint {
    fun repository(): MasstamilanRepository
    fun playbackManager(): PlaybackManager
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    initialQuery: String = "",
    viewModel: SearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(appContext, SearchEntryPoint::class.java)
    }
    val repository = remember { entryPoint.repository() }
    val playbackManager = remember { entryPoint.playbackManager() }
    val scope = rememberCoroutineScope()

    val state by viewModel.uiState.collectAsState()
    var playingId by remember { mutableStateOf<Int?>(null) }
    var playingBusy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery)
    }

    fun instantPlay(item: RankedSong) {
        scope.launch {
            playingBusy = true
            try {
                val song = item.song
                // song.dlPath may be a page path or direct downloader link
                val streamUrl = when {
                    song.dlPath.startsWith("/downloader/") ->
                        "https://www.masstamilan.dev" + song.dlPath
                    song.dlPath.startsWith("http") -> song.dlPath
                    item.movieSlug.isNotBlank() ->
                        repository.resolveStreamUrl(item.movieSlug)
                    song.dlPath.isNotBlank() ->
                        repository.resolveStreamUrl(song.dlPath.trim('/'))
                    else -> null
                }
                if (streamUrl != null) {
                    playbackManager.playStream(context, streamUrl, song.name, song.artists)
                    playingId = song.id
                    navController.navigate("player/${song.id}")
                } else {
                    toast = "Couldn't resolve stream for \"${song.name}\""
                }
            } catch (_: Exception) {
                toast = "Playback failed — check connection"
            } finally {
                playingBusy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search songs, artists, movies…", color = TextHint) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, "Search", tint = TextHint) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = TextHint,
                            cursorColor = Primary
                        )
                    )
                },
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
            // Autocomplete suggestion chips (instant, tappable)
            if (state.suggestions.isNotEmpty() && state.songs.isEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    state.suggestions.forEach { s ->
                        Text(
                            text = s.name,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onQueryChange(s.name) }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
            when {
                state.isSearching -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Primary) }

                state.error != null && state.songs.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::retry,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) { Text("Retry") }
                    }
                }

                state.songs.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.query.trim().length < 2) "Type 2+ letters to search songs"
                        else "No results yet",
                        color = TextHint
                    )
                }

                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    if (state.searchedQuery.isNotBlank()) {
                        item {
                            Text(
                                "${state.songs.size} songs for \"${state.searchedQuery}\"",
                                color = TextHint,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    items(state.songs, key = { it.song.id to it.song.name }) { item ->
                        RankedSongRow(
                            item = item,
                            isPlaying = playingId == item.song.id,
                            busy = playingBusy,
                            onPlay = { instantPlay(item) },
                            onDownload = {
                                scope.launch {
                                    navController.navigate("downloads")
                                }
                            }
                        )
                    }
                }
            }
            toast?.let {
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(2500)
                    toast = null
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(it, color = TextSecondary, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
fun RankedSongRow(
    item: RankedSong,
    isPlaying: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val song = item.song
    val artUrl = remember(song.imageName) {
        when {
            song.imageName.startsWith("http") -> song.imageName
            song.imageName.isNotBlank() -> {
                // img_name is extension-less ("jailer-2-tamil-2026"); /i/<name> alone 404s.
                val file = if (song.imageName.contains('.')) song.imageName else "${song.imageName}.jpg"
                "https://www.masstamilan.dev/i/$file"
            }
            else -> null
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onPlay),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (artUrl != null) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = song.movieName,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(song.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    listOf(song.artists, song.movieName).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2
                )
                if (song.duration.isNotBlank()) {
                    Text(song.duration, style = MaterialTheme.typography.labelSmall, color = TextHint)
                }
            }
            if (busy && isPlaying) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Default.PlayArrow, "Play",
                        tint = if (isPlaying) Primary else TextPrimary
                    )
                }
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, "Download", tint = TextSecondary)
            }
        }
    }
}

// Legacy stubs kept for binary compat with older call sites (not used by new UI).
@Composable
fun SearchResultItemLegacy(
    name: String,
    navController: NavController
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}
