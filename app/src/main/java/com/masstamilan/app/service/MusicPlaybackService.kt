package com.masstamilan.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.media3.session.MediaSession
import com.masstamilan.app.core.media.PlaybackManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : Service() {
    @Inject lateinit var playbackManager: PlaybackManager
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playbackManager.createPlayer(this)
        mediaSession = playbackManager.createMediaSession(this, player)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Live title/artist so the notification always reflects the current track.
        val notification = playbackManager.buildNotification(
            this,
            playbackManager.currentTitle().ifBlank { "Playing" },
            playbackManager.currentArtist()
        )
        startForeground(1, notification)

        // Route through the album queue (lazy URL resolving). ExoPlayer's own
        // seekToNext/Previous would be no-ops on our single-item player queue.
        when (intent?.action) {
            ACTION_PLAY -> playbackManager.getPlayer()?.play()
            ACTION_PAUSE -> playbackManager.getPlayer()?.pause()
            ACTION_NEXT -> playbackManager.nextInAlbum()
            ACTION_PREVIOUS -> playbackManager.previousInAlbum()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession?.release()
        playbackManager.releasePlayer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.masstamilan.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.masstamilan.app.ACTION_PAUSE"
        const val ACTION_NEXT = "com.masstamilan.app.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.masstamilan.app.ACTION_PREVIOUS"
    }
}
