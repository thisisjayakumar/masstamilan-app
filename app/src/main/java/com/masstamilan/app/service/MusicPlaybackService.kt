package com.masstamilan.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : Service() {
    @Inject lateinit var playbackManager: com.masstamilan.app.core.media.PlaybackManager
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playbackManager.createPlayer(this)
        mediaSession = playbackManager.createMediaSession(this, player)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = playbackManager.buildNotification(this, "Playing", "")
        startForeground(1, notification)

        val action = intent?.action
        when (action) {
            "com.masstamilan.app.ACTION_PLAY" -> playbackManager.getPlayer()?.play()
            "com.masstamilan.app.ACTION_PAUSE" -> playbackManager.getPlayer()?.pause()
            "com.masstamilan.app.ACTION_NEXT" -> playbackManager.getPlayer()?.seekToNext()
            "com.masstamilan.app.ACTION_PREVIOUS" -> playbackManager.getPlayer()?.seekToPrevious()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession?.release()
        playbackManager.releasePlayer()
        super.onDestroy()
    }
}
