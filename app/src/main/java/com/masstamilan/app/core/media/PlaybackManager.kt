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
import com.masstamilan.app.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var currentUrl: String? = null

    fun createPlayer(context: Context): ExoPlayer {
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

    /**
     * Instant play: set a single stream URL and start. Safe to call repeatedly;
     * no-ops when the same URL is already loaded (avoids re-buffering on
     * recomposition / double-tap).
     * @return true if playback started (or was already playing this URL).
     */
    fun playStream(context: Context, url: String, title: String = "", artist: String = ""): Boolean {
        if (!isPlayableUrl(url)) return false
        val exo = createPlayer(context)
        if (url == currentUrl && exo.mediaItemCount > 0) {
            exo.play()
            return true
        }
        currentUrl = url
        val item = androidx.media3.common.MediaItem.Builder()
            .setUri(android.net.Uri.parse(url))
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title.ifBlank { "Playing" })
                    .setArtist(artist)
                    .build()
            )
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.play()
        return true
    }

    fun playQueue(context: Context, urls: List<String>, startIndex: Int = 0) {
        if (urls.isEmpty()) return
        val exo = createPlayer(context)
        val items = urls.filter(::isPlayableUrl).map {
            androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(it))
        }
        if (items.isEmpty()) return
        exo.setMediaItems(items, startIndex.coerceIn(items.indices), 0L)
        exo.prepare()
        exo.play()
        currentUrl = urls[startIndex.coerceIn(urls.indices)]
    }

    fun stopAndClear() {
        player?.stop()
        player?.clearMediaItems()
        currentUrl = null
    }

    companion object {
        /** Pure + unit-tested: only https MP3/downloader URLs are playable. */
        fun isPlayableUrl(url: String): Boolean {
            if (url.isBlank()) return false
            val u = url.trim()
            if (!u.startsWith("https://")) return false
            if (u.contains(" ")) return false
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
