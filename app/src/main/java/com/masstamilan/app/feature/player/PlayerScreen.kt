package com.masstamilan.app.feature.player

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.masstamilan.app.core.di.AppEntryPoint
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.service.MusicPlaybackService
import com.masstamilan.app.ui.theme.*
import dagger.hilt.android.EntryPointAccessors
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
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(player.duration.coerceAtLeast(0L)) }
    var position by remember { mutableStateOf(player.currentPosition) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(nowPlaying: Boolean) {
                isPlaying = nowPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
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
            progress = if (duration > 0) position.toFloat() / duration else 0f
            delay(if (isPlaying) 500 else 1000)
        }
    }

    LaunchedEffect(Unit) {
        resolvedManager.createMediaSession(context, player)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = SurfaceVariant,
                contentColor = TextPrimary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { player.seekToPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, "Previous", tint = TextPrimary, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = {
                        if (isPlaying) player.pause() else player.play()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = Primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    IconButton(onClick = { player.seekToNext() }) {
                        Icon(Icons.Default.SkipNext, "Next", tint = TextPrimary, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Album Art Placeholder
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .clip(CircleShape)
                    .background(SurfaceVariant)
            ) {
                Text(movieSlugFromId(songId), style = MaterialTheme.typography.headlineLarge, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                resolvedManager.currentTitle().ifBlank { movieSlugFromId(songId) },
                style = MaterialTheme.typography.titleLarge, color = TextPrimary
            )
            Text(
                resolvedManager.currentArtist().ifBlank { "Tap play to start" },
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Progress bar
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text("${formatTime(position)} / ${formatTime(duration)}", style = MaterialTheme.typography.labelSmall, color = TextHint)
                Slider(
                    value = progress,
                    onValueChange = {
                        progress = it
                        if (duration > 0) player.seekTo((it * duration).toLong())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = Primary)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row {
                IconButton(onClick = { /* shuffle */ }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { /* repeat */ }) {
                    Icon(Icons.Default.Loop, "Loop", tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

fun movieSlugFromId(id: String): String = id.replace("_", " ")
fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}
