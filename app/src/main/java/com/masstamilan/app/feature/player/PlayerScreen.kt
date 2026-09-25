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
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.service.MusicPlaybackService
import com.masstamilan.app.ui.theme.*

@Composable
fun PlayerScreen(navController: NavController, songId: String, playbackManager: PlaybackManager = remember { PlaybackManager() }) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf(0L) }

    val player = playbackManager.getPlayer() ?: remember { playbackManager.createPlayer(context) }

    LaunchedEffect(Unit) {
        playbackManager.createMediaSession(context, player)
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
                        isPlaying = !isPlaying
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
            Text("Song Title", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text("Artist Name", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            Spacer(modifier = Modifier.height(32.dp))

            // Progress bar
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text("${formatTime(position)} / ${formatTime(duration)}", style = MaterialTheme.typography.labelSmall, color = TextHint)
                Slider(
                    value = progress,
                    onValueChange = { progress = it },
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
