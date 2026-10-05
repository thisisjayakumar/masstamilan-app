package com.masstamilan.app.core.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.masstamilan.app.data.model.QueueTrack
import com.masstamilan.app.data.remote.MasstamilanApi
import com.masstamilan.app.data.remote.MasstamilanParsers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadOption(val quality: String, val url: String)

@Singleton
class DownloadHelper @Inject constructor(
    private val client: OkHttpClient,
    private val api: MasstamilanApi,
    @ApplicationContext private val context: Context
) {
    companion object {
        /**
         * Pure + unit-tested: strip chars illegal in FAT32/MediaStore display
         * names. Unicode-aware (Tamil script preserved), blanks fall back.
         */
        fun sanitizeFileName(songName: String, artist: String): String {
            val base = listOf(songName.trim(), artist.trim())
                .filter { it.isNotBlank() }
                .joinToString(" - ")
                .replace(Regex("""[^\p{L}\p{M}\p{N}_.\- ]"""), "_")
                .trim()
            return base.ifBlank { "track" }
        }
    }

    /** Qualities available on a song page (usually 2: 128kbps, 320kbps). */
    suspend fun songQualities(pagePath: String): List<DownloadOption> = withContext(Dispatchers.IO) {
        try {
            MasstamilanParsers.extractDownloadLinks(api.getSongPage(pagePath))
                .map { entry -> DownloadOption(quality = entry.key, url = entry.value) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Download to MediaStore (API 29+) with IS_PENDING, legacy fallback ≤ 28. */
    suspend fun download(
        url: String,
        songName: String,
        artist: String,
        onProgress: ((Float, Long, Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
            val total = resp.body?.contentLength() ?: 0L
            val tmp = File(context.cacheDir, "${System.currentTimeMillis()}.mp3")
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        onProgress?.invoke(
                            if (total > 0) done.toFloat() / total else 0f, done, total
                        )
                    }
                }
            }
            val dest = if (android.os.Build.VERSION.SDK_INT >= 29) {
                mediaStoreInsert(songName, artist, tmp)
            } else {
                legacyMove(songName, artist, tmp)
            }
            Result.success(dest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mediaStoreInsert(songName: String, artist: String, tmp: File): File {
        val safe = sanitizeFileName(songName, artist)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safe.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MUSIC + "/MasstamilanApp")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return legacyMove(songName, artist, tmp)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            tmp.inputStream().copyTo(out)
        }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        tmp.delete()
        return File(uri.toString())
    }

    private fun legacyMove(songName: String, artist: String, tmp: File): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "MasstamilanApp"
        )
        if (!dir.exists()) dir.mkdirs()
        val safe = sanitizeFileName(songName, artist)
        val dest = File(dir, "$safe.mp3")
        tmp.renameTo(dest)
        return dest
    }
}
