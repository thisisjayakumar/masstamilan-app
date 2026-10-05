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
import com.masstamilan.app.core.util.Artwork
import com.masstamilan.app.core.util.DownloadHelper
import com.masstamilan.app.data.model.DownloadEntity
import com.masstamilan.app.data.model.RankedSong
import com.masstamilan.app.data.model.pagePathOf
import com.masstamilan.app.data.model.toQueue
import com.masstamilan.app.data.remote.MasstamilanApi
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
    fun downloadHelper(): DownloadHelper
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
    val playbackManager = remember { entryPoint.playbackManager() }
    val repository = remember { entryPoint.repository() }
    val downloadHelper = remember { entryPoint.downloadHelper() }
    val scope = rememberCoroutineScope()

    val state by viewModel.uiState.collectAsState()
    var playingId by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery)
    }

    fun instantPlay(item: RankedSong) {
        // Whole result set becomes the queue; the manager resolves each URL lazily.
        val queue = state.songs.map { it.song }.toQueue()
        val index = queue.indexOfFirst { it.songId == item.song.id }.takeIf { it >= 0 } ?: 0
        if (playbackManager.playQueue(context, queue, index)) {
            playingId = item.song.id
            navController.navigate("player/${item.song.id}")
        } else {
            toast = "Couldn't resolve stream for \"${item.song.name}\""
        }
    }

    fun enqueueDownload(item: RankedSong) {
        scope.launch {
            val song = item.song
            toast = "Starting download: ${song.name}"
            // Fast path: albumTracks dl_path is already a signed stream URL.
            val direct = song.dlPath.trim().takeIf { it.startsWith("/downloader/") }
                ?.let { MasstamilanApi.BASE_URL + it }
            val (url, quality) = if (direct != null) {
                direct to if ("d320" in song.dlPath) "320kbps" else "128kbps"
            } else {
                val pagePath = pagePathOf(song.dlPath)
                if (pagePath.isBlank()) {
                    toast = "No download link for \"${song.name}\""
                    return@launch
                }
                val options = downloadHelper.songQualities(pagePath)
                val best = options.firstOrNull { it.quality == "320kbps" }
                    ?: options.firstOrNull()
                if (best == null) {
                    toast = "No download link for \"${song.name}\""
                    return@launch
                }
                best.url to best.quality
            }
            val rowId = repository.insertDownload(
                DownloadEntity(
                    songId = song.id,
                    songName = song.name,
                    artist = song.artists,
                    movieName = song.movieName,
                    downloadUrl = url,
                    quality = quality,
                    status = "downloading"
                )
            )
            var lastPushed = 0f
            val result = downloadHelper.download(url, song.name, song.artists) { p, done, total ->
                // Throttle Room writes: push at most every ~5% of progress.
                if (p - lastPushed >= 0.05f || (total > 0 && done >= total)) {
                    lastPushed = p
                    scope.launch {
                        repository.updateProgress(rowId, "downloading", p, done, total)
                    }
                }
            }
            result.fold(
                onSuccess = { file ->
                    scope.launch {
                        repository.markCompleted(rowId, file.path)
                        toast = "Downloaded \"${song.name}\""
                        navController.navigate("downloads")
                    }
                },
                onFailure = {
                    scope.launch {
                        repository.markFailed(rowId)
                        toast = "Download failed — check connection"
                    }
                }
            )
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
                            onPlay = { instantPlay(item) },
                            onDownload = { enqueueDownload(item) }
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
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val song = item.song
    val artUrl = remember(song.imageName) {
        Artwork.url(song.imageName).ifBlank { null }
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
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow, "Play",
                    tint = if (isPlaying) Primary else TextPrimary
                )
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
