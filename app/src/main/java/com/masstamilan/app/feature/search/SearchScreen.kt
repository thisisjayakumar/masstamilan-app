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
import androidx.compose.material.icons.filled.MoreVert
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
import com.masstamilan.app.core.settings.UserPreferences
import com.masstamilan.app.core.util.Artwork
import com.masstamilan.app.core.util.DownloadHelper
import com.masstamilan.app.core.util.DownloadOption
import com.masstamilan.app.data.model.RankedSong
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.model.toQueue
import com.masstamilan.app.data.repository.MasstamilanRepository
import com.masstamilan.app.feature.common.SongActionsSheet
import com.masstamilan.app.feature.downloads.DownloadQualityDialog
import com.masstamilan.app.feature.downloads.performChosenDownload
import com.masstamilan.app.feature.downloads.resolveDownloadOptions
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
    fun userPreferences(): UserPreferences
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
    val prefs = remember { entryPoint.userPreferences() }
    val scope = rememberCoroutineScope()

    val state by viewModel.uiState.collectAsState()
    var playingId by remember { mutableStateOf<Int?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var menuSong by remember { mutableStateOf<RankedSong?>(null) }
    // Quality-picker state: always shown before any download starts.
    var pendingSong by remember { mutableStateOf<SongResult?>(null) }
    var qualityOptions by remember { mutableStateOf<List<DownloadOption>?>(null) }
    var selectedQuality by remember { mutableStateOf<String?>(null) }

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
        // Always show the quality picker first — never auto-pick.
        scope.launch {
            pendingSong = item.song
            qualityOptions = null
            selectedQuality = null
            val options = resolveDownloadOptions(item.song, downloadHelper)
            qualityOptions = options
            // Pre-select preferred quality when available, user can still change it.
            val wantHigh = try { prefs.preferHighQualityDownload() } catch (_: Exception) { true }
            selectedQuality = if (wantHigh) {
                options.firstOrNull { it.quality == "320kbps" }?.quality
                    ?: options.firstOrNull()?.quality
            } else {
                options.firstOrNull { it.quality == "128kbps" }?.quality
                    ?: options.firstOrNull()?.quality
            }
            if (options.isEmpty()) {
                toast = "No download link for \"${item.song.name}\""
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
                            onPlay = { instantPlay(item) },
                            onDownload = { enqueueDownload(item) },
                            onMore = { menuSong = item }
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
            menuSong?.let { item ->
                SongActionsSheet(
                    song = item.song,
                    movieSlug = item.movieSlug,
                    onDismiss = { menuSong = null },
                    onPlay = { instantPlay(item) },
                    onDownload = { enqueueDownload(item) },
                    onAlbum = {
                        val slug = item.movieSlug.trim('/').ifBlank { return@SongActionsSheet }
                        navController.navigate("song_detail/$slug")
                    }
                )
            }
            pendingSong?.let { song ->
                DownloadQualityDialog(
                    songName = song.name,
                    options = qualityOptions,
                    selectedQuality = selectedQuality,
                    onSelect = { selectedQuality = it },
                    onConfirm = {
                        val chosen = qualityOptions?.firstOrNull { it.quality == selectedQuality }
                        pendingSong = null
                        if (chosen != null) {
                            performChosenDownload(
                                scope, repository, downloadHelper, navController,
                                song, chosen
                            ) { toast = it }
                        }
                    },
                    onDismiss = { pendingSong = null }
                )
            }
        }
    }
}

@Composable
fun RankedSongRow(
    item: RankedSong,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onMore: () -> Unit
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
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, "More options", tint = TextSecondary)
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
