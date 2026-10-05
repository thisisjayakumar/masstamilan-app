package com.masstamilan.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.ui.theme.Primary
import com.masstamilan.app.ui.theme.Surface as SurfaceColor
import com.masstamilan.app.ui.theme.TextPrimary
import com.masstamilan.app.ui.theme.TextSecondary

/**
 * Persistent mini-player pinned above the bottom nav (Spotify-style).
 * Hidden when nothing is queued. Tap opens the full player.
 */
@Composable
fun MiniPlayer(
    navController: NavController,
    playbackManager: PlaybackManager
) {
    val queue by playbackManager.queueFlow.collectAsState()
    val index by playbackManager.currentIndexFlow.collectAsState()
    if (queue.isEmpty() || index !in queue.indices) return
    val track = queue[index]
    val player = playbackManager.getPlayer() ?: return

    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(nowPlaying: Boolean) {
                isPlaying = nowPlaying
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        onDispose { player.removeListener(listener) }
    }

    val title = playbackManager.currentTitle().ifBlank { track.title.ifBlank { "Playing" } }
    val artist = playbackManager.currentArtist().ifBlank { track.artist }
    val artwork = playbackManager.currentArtwork().ifBlank { track.artwork.ifBlank { null } }

    Surface(
        tonalElevation = 8.dp,
        color = SurfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("player/${track.songId}") }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (artwork != null) {
                AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.size(44.dp))
            } else {
                Icon(Icons.Default.MusicNote, null, tint = TextSecondary, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (artist.isNotBlank()) {
                    Text(
                        artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
