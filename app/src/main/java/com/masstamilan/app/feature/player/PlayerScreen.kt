package com.masstamilan.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.masstamilan.app.core.di.AppEntryPoint
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.ui.theme.*
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navController: NavController, songId: String, playbackManager: PlaybackManager? = null) {
    val context = LocalContext.current
    // Use the Hilt singleton so this screen controls the same ExoPlayer that
    // Search/Album screens started. A fresh PlaybackManager() would hold an
    // empty player and play nothing.
    val resolvedManager = playbackManager ?: remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, AppEntryPoint::class.java
        ).playbackManager()
    }

    val player = remember(context) {
        resolvedManager.getPlayer() ?: resolvedManager.createPlayer(context)
    }
    // Recompose on track change so title/artist/artwork refresh without navigation.
    val queue by resolvedManager.queueFlow.collectAsState()
    val trackIndex by resolvedManager.currentIndexFlow.collectAsState()
    val canSkip = queue.size > 1 && trackIndex >= 0
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableStateOf(player.currentPosition) }
    var duration by remember { mutableStateOf(player.duration.coerceAtLeast(0L)) }
    var shuffleOn by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeatOne by remember { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    var error by remember { mutableStateOf<String?>(null) }

    val title = resolvedManager.currentTitle().ifBlank { "Playing" }
    val artist = resolvedManager.currentArtist()
    val artwork = resolvedManager.currentArtwork().ifBlank { null }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(nowPlaying: Boolean) {
                isPlaying = nowPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                error = "Couldn't play this track — check your connection"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Poll position while playing (event-driven duration above).
    LaunchedEffect(player, isPlaying) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            if (error == null && player.playerError != null) {
                error = "Couldn't play this track — check your connection"
            }
            delay(if (isPlaying) 500 else 1000)
        }
    }

    LaunchedEffect(Unit) {
        resolvedManager.createMediaSession(context, player)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("PLAYING FROM ALBUM", color = TextSecondary, fontSize = 11.sp, letterSpacing = 1.5.sp)
                        Text(
                            title, color = TextPrimary, fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { /* queue / more */ }) {
                        Icon(Icons.Default.FavoriteBorder, "Like", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF2A2A2A), Color(0xFF121212), Color(0xFF121212))
                    )
                )
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Album art — square with rounded corners, Spotify-style.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
            ) {
                if (artwork != null) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextHint,
                        modifier = Modifier
                            .size(96.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Title + artist, full width, left aligned like Spotify.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (artist.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            artist,
                            color = TextSecondary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Slider(
                value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { fraction ->
                    if (duration > 0) {
                        position = (fraction * duration).toLong()
                        player.seekTo(position)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = TextPrimary,
                    activeTrackColor = TextPrimary,
                    inactiveTrackColor = SurfaceVariant
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position), color = TextSecondary, fontSize = 12.sp)
                Text(formatTime(duration), color = TextSecondary, fontSize = 12.sp)
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error!!,
                    color = Error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Transport row: shuffle | prev | play/pause | next | repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    shuffleOn = !shuffleOn
                    player.shuffleModeEnabled = shuffleOn
                }) {
                    Icon(
                        Icons.Default.Shuffle, "Shuffle",
                        tint = if (shuffleOn) Primary else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(
                    onClick = { resolvedManager.previousInAlbum() },
                    enabled = canSkip
                ) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = TextPrimary, modifier = Modifier.size(40.dp))
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(TextPrimary)
                        .clickable {
                            error = null
                            if (isPlaying) player.pause() else player.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF121212),
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(
                    onClick = { resolvedManager.nextInAlbum() },
                    enabled = canSkip
                ) {
                    Icon(Icons.Default.SkipNext, "Next", tint = TextPrimary, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = {
                    repeatOne = !repeatOne
                    player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                }) {
                    Icon(
                        if (repeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        tint = if (repeatOne) Primary else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
