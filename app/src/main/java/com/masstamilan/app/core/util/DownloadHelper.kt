package com.masstamilan.app.core.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.masstamilan.app.data.model.DownloadEntity
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.core.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadHelper @Inject constructor(
    private val client: OkHttpClient,
    private val networkHelper: NetworkHelper,
    private val playbackManager: PlaybackManager
) {
    suspend fun downloadSong(
        url: String,
        songName: String,
        artist: String,
        quality: String,
        onProgress: (Float, Long, Long) -> Unit
    ): File? {
        if (!networkHelper.isConnected()) return null

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response: Response = client.newCall(request).execute()

                if (!response.isSuccessful) return@withContext null

                val totalBytes = response.body?.contentLength() ?: 0L
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "MasstamilanApp"
                )
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val file = File(downloadDir, "$songName - $artist.mp3")
                var downloadedBytes = 0L

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            onProgress(downloadedBytes.toFloat() / totalBytes, downloadedBytes, totalBytes)
                        }
                    }
                }

                file
            } catch (e: Exception) {
                null
            }
        }
    }

    fun getDownloadedFiles(): List<File> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "MasstamilanApp"
        )
        return if (dir.exists()) dir.listFiles { _, name -> name.endsWith(".mp3") }.toList() else emptyList()
    }
}
