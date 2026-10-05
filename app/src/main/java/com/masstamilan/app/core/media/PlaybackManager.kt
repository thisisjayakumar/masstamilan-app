package com.masstamilan.app.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.masstamilan.app.R
import com.masstamilan.app.data.model.QueueTrack
import com.masstamilan.app.data.model.stepIndex
import com.masstamilan.app.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Resolves a song-page path to a stream URL. Injected so the manager stays network-agnostic. */
fun interface StreamResolver {
    suspend fun resolve(pagePath: String): String?
}

@Singleton
class PlaybackManager @Inject constructor(
    private val streamResolver: StreamResolver
) {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentUrl: String? = null
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentArtwork: String = ""

    private val _queue = MutableStateFlow(emptyList<QueueTrack>())
    val queueFlow: StateFlow<List<QueueTrack>> = _queue
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndexFlow: StateFlow<Int> = _currentIndex

    fun createPlayer(context: Context): ExoPlayer {
        appContext = context.applicationContext
        if (player == null) {
            player = ExoPlayer.Builder(context)
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                ).build()
        }
        return player!!
    }

    fun getPlayer(): ExoPlayer? = player

    fun currentStreamUrl(): String? = currentUrl

    fun currentTitle(): String = currentTitle

    fun currentArtist(): String = currentArtist

    /** Absolute album-art URL for the current track ("" when unknown). */
    fun currentArtwork(): String = currentArtwork

    /**
     * Single track = one-item queue. Returns false when nothing playable.
     * Song-page paths resolve lazily off the main thread.
     */
    fun playStream(
        context: Context,
        url: String,
        title: String = "",
        artist: String = "",
        artwork: String = ""
    ): Boolean = playTrack(
        context,
        QueueTrack(streamUrl = url.takeIf(::isPlayableUrl), title = title, artist = artist, artwork = artwork)
    )

    /**
     * Album/result list with start position. Entries without a direct URL
     * resolve via [StreamResolver] when reached. Returns false when empty.
     */
    fun playQueue(context: Context, tracks: List<QueueTrack>, startIndex: Int = 0): Boolean {
        if (tracks.isEmpty()) return false
        appContext = context.applicationContext
        _queue.value = tracks
        scope.launch { loadAt(startIndex.coerceIn(tracks.indices), autoplay = true) }
        return true
    }

    /** Next track in the current album, wrapping to the first. False when N/A. */
    fun nextInAlbum(): Boolean = step(+1)

    /** Previous track in the current album, wrapping to the last. False when N/A. */
    fun previousInAlbum(): Boolean = step(-1)

    private fun playTrack(context: Context, track: QueueTrack): Boolean {
        if (track.streamUrl == null && track.songPagePath.isBlank()) return false
        appContext = context.applicationContext
        _queue.value = listOf(track)
        scope.launch { loadAt(0, autoplay = true) }
        return true
    }

    private fun step(delta: Int): Boolean {
        val queue = _queue.value
        if (queue.size < 2 || appContext == null) return false
        val wasPlaying = player?.playWhenReady == true
        scope.launch { loadAt(stepIndex(_currentIndex.value, queue.size, delta), autoplay = wasPlaying) }
        return true
    }

    private suspend fun loadAt(index: Int, autoplay: Boolean) {
        val context = appContext ?: return
        val track = _queue.value.getOrNull(index) ?: return
        val url = track.streamUrl
            ?: track.songPagePath.takeIf { it.isNotBlank() }?.let {
                try {
                    streamResolver.resolve(it)
                } catch (_: Exception) {
                    null
                }
            } ?: return
        if (!isPlayableUrl(url)) return
        val exo = createPlayer(context)
        if (url == currentUrl && exo.mediaItemCount > 0) {
            if (autoplay) exo.play()
            _currentIndex.value = index
            return
        }
        currentUrl = url
        currentTitle = track.title.ifBlank { "Playing" }
        currentArtist = track.artist
        currentArtwork = track.artwork
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(currentTitle)
            .setArtist(currentArtist)
            .apply { if (currentArtwork.isNotBlank()) setArtworkUri(android.net.Uri.parse(currentArtwork)) }
            .build()
        exo.setMediaItem(
            androidx.media3.common.MediaItem.Builder()
                .setUri(android.net.Uri.parse(url))
                .setMediaMetadata(metadata)
                .build()
        )
        exo.prepare()
        exo.playWhenReady = autoplay
        _currentIndex.value = index
    }

    fun stopAndClear() {
        player?.stop()
        player?.clearMediaItems()
        currentUrl = null
        currentArtwork = ""
        _queue.value = emptyList()
        _currentIndex.value = -1
    }

    companion object {
        /**
         * Pure + unit-tested gate for what the player accepts:
         * https streams (MP3 / downloader / CDN) plus local
         * `content://` / `file://` URIs from completed downloads.
         */
        fun isPlayableUrl(url: String): Boolean {
            val u = url.trim()
            if (u.isBlank() || u.contains(" ")) return false
            if (u.startsWith("content://") || u.startsWith("file://")) return true
            if (!u.startsWith("https://")) return false
            return u.endsWith(".mp3") || "/downloader/" in u || "cdn" in u
        }

        /** Build the absolute stream URL from a site-relative dl path. */
        fun absoluteStreamUrl(dlPath: String, baseUrl: String = "https://www.masstamilan.dev"): String? {
            val p = dlPath.trim()
            if (p.isBlank()) return null
            if (p.startsWith("http")) return p
            if (p.startsWith("/downloader/")) return baseUrl + p
            return null
        }
    }

    fun releasePlayer() {
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
    }

    fun createMediaSession(context: Context, player: ExoPlayer): MediaSession {
        if (mediaSession == null) {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaSession = MediaSession.Builder(context, player)
                .setSessionActivity(pendingIntent)
                .setCallback(object : MediaSession.Callback {
                    override fun onAddMediaItems(
                        mediaSession: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        mediaItems: MutableList<MediaItem>
                    ): ListenableFuture<MutableList<MediaItem>> {
                        return Futures.immediateFuture(mediaItems)
                    }
                })
                .build()
        }
        return mediaSession!!
    }

    fun buildNotification(context: Context, songName: String, artist: String): Notification {
        val channelId = "masstamilan_playback"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val pauseIntent = PendingIntent.getBroadcast(
            context, 0, Intent("com.masstamilan.app.ACTION_PAUSE"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getBroadcast(
            context, 0, Intent("com.masstamilan.app.ACTION_NEXT"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getBroadcast(
            context, 0, Intent("com.masstamilan.app.ACTION_PREVIOUS"),
            PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(songName)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(deleteIntent)
            .addAction(R.drawable.ic_previous, "Previous", prevIntent)
            .addAction(R.drawable.ic_pause, "Pause", pauseIntent)
            .addAction(R.drawable.ic_next, "Next", nextIntent)
            .setOngoing(true)
            .build()
    }
}
