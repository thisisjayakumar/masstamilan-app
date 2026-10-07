package com.masstamilan.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.masstamilan.app.core.util.Json
import com.masstamilan.app.data.entity.FavoriteEntity
import com.masstamilan.app.data.entity.PlaylistEntity
import com.masstamilan.app.data.entity.PlaylistSongEntity
import com.masstamilan.app.data.model.QueueTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable per-song metadata kept in a library backup. Signed stream URLs are
 * deliberately excluded — they expire within a day and are re-resolved lively
 * from [songPagePath] on play.
 */
data class BackupSong(
    val songId: Int = 0,
    val name: String = "",
    val artists: String = "",
    val movieName: String = "",
    val movieSlug: String = "",
    val songPagePath: String = "",
    val imageName: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

data class BackupPlaylist(
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val songs: List<BackupSong> = emptyList()
)

data class LibraryBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val favorites: List<BackupSong> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList()
)

data class BackupInfo(
    val exists: Boolean,
    val exportedAt: Long = 0,
    val sizeBytes: Long = 0,
    val favorites: Int = 0,
    val playlists: Int = 0,
    val songs: Int = 0
)

fun FavoriteEntity.toBackupSong() = BackupSong(
    songId = songId, name = name, artists = artists, movieName = movieName,
    movieSlug = movieSlug, songPagePath = songPagePath, imageName = imageName,
    addedAt = addedAt
)

fun PlaylistSongEntity.toBackupSong() = BackupSong(
    songId = songId, name = name, artists = artists, movieName = movieName,
    movieSlug = movieSlug, songPagePath = songPagePath, imageName = imageName,
    addedAt = id
)

fun BackupSong.toQueueTrack() = QueueTrack(
    title = name, artist = artists, artwork = imageName,
    songPagePath = songPagePath, movieSlug = movieSlug, songId = songId
)

private fun BackupSong.toMap(): Map<String, Any?> = mapOf(
    "songId" to songId, "name" to name, "artists" to artists,
    "movieName" to movieName, "movieSlug" to movieSlug,
    "songPagePath" to songPagePath, "imageName" to imageName, "addedAt" to addedAt
)

/** Pure + JVM unit-tested: library → compact JSON (tens of KB for 100s of songs). */
fun encodeLibraryBackup(backup: LibraryBackup): String = Json.write(
    mapOf(
        "version" to backup.version,
        "app" to "MasstamilanApp",
        "exportedAt" to backup.exportedAt,
        "favorites" to backup.favorites.map { it.toMap() },
        "playlists" to backup.playlists.map { pl ->
            mapOf(
                "name" to pl.name, "createdAt" to pl.createdAt,
                "updatedAt" to pl.updatedAt,
                "songs" to pl.songs.map { it.toMap() }
            )
        }
    )
)

@Suppress("UNCHECKED_CAST")
private fun asMap(v: Any?): Map<String, Any?> = (v as? Map<*, *>)
    ?.entries?.associate { (k, x) -> k.toString() to x } ?: emptyMap()

private fun str(m: Map<String, Any?>, k: String): String = when (val v = m[k]) {
    is String -> v
    is Number -> if (v.toDouble() == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    else -> ""
}

private fun long(m: Map<String, Any?>, k: String): Long =
    (m[k] as? Number)?.toLong() ?: (m[k] as? String)?.toLongOrNull() ?: 0L

private fun int(m: Map<String, Any?>, k: String): Int =
    (m[k] as? Number)?.toInt() ?: (m[k] as? String)?.toIntOrNull() ?: 0

private fun songList(v: Any?): List<BackupSong> {
    val out = mutableListOf<BackupSong>()
    (v as? List<*>)?.forEach { item ->
        val m = asMap(item)
        val name = str(m, "name")
        if (name.isBlank()) return@forEach
        out.add(
            BackupSong(
                songId = int(m, "songId"), name = name, artists = str(m, "artists"),
                movieName = str(m, "movieName"), movieSlug = str(m, "movieSlug"),
                songPagePath = str(m, "songPagePath"), imageName = str(m, "imageName"),
                addedAt = long(m, "addedAt").takeIf { it > 0 } ?: System.currentTimeMillis()
            )
        )
    }
    return out
}

/** Pure + JVM unit-tested: JSON → library. Never throws; corrupt input → failure. */
fun decodeLibraryBackup(text: String): Result<LibraryBackup> = try {
    val root = asMap(Json.parse(text.trim()))
    if (root.isEmpty()) throw IllegalArgumentException("empty backup")
    val playlists = mutableListOf<BackupPlaylist>()
    ((root["playlists"] as? List<*>) ?: emptyList<Any?>()).forEach { item ->
        val m = asMap(item)
        val name = str(m, "name")
        if (name.isBlank()) return@forEach
        playlists.add(
            BackupPlaylist(
                name = name,
                createdAt = long(m, "createdAt").takeIf { it > 0 } ?: System.currentTimeMillis(),
                updatedAt = long(m, "updatedAt").takeIf { it > 0 } ?: System.currentTimeMillis(),
                songs = songList(m["songs"])
            )
        )
    }
    Result.success(
        LibraryBackup(
            version = int(root, "version").takeIf { it > 0 } ?: 1,
            exportedAt = long(root, "exportedAt").takeIf { it > 0 } ?: System.currentTimeMillis(),
            favorites = songList(root["favorites"]),
            playlists = playlists
        )
    )
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Single location for library persistence across reinstalls: exports the
 * library (favorites + playlists, metadata only — no audio, no expiring
 * URLs) to shared Documents storage that survives uninstall, and restores,
 * inspects or deletes it. Light: one small JSON file, fully lazy I/O.
 */
@Singleton
class LibraryBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository
) {
    companion object {
        const val FILE_NAME = "masstamilan-library.json"
        const val RELATIVE_DIR = "Documents/MasstamilanApp"
        private const val MIME = "application/json"
    }

    private fun legacyFile(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "MasstamilanApp/$FILE_NAME"
        )

    private fun findBackupUri(): Uri? {
        if (Build.VERSION.SDK_INT < 29) {
            return legacyFile().takeIf { it.exists() }?.let { Uri.fromFile(it) }
        }
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Files.FileColumns.DISPLAY_NAME}=? AND " +
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(FILE_NAME, "$RELATIVE_DIR%"),
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            if (cursor.moveToFirst()) {
                return Uri.withAppendedPath(collection, cursor.getLong(idCol).toString())
            }
        }
        return null
    }

    suspend fun backupInfo(): BackupInfo = withContext(Dispatchers.IO) {
        try {
            val uri = findBackupUri() ?: return@withContext BackupInfo(exists = false)
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@withContext BackupInfo(exists = false)
            val backup = decodeLibraryBackup(text).getOrNull()
                ?: return@withContext BackupInfo(exists = true)
            BackupInfo(
                exists = true,
                exportedAt = backup.exportedAt,
                sizeBytes = text.toByteArray().size.toLong(),
                favorites = backup.favorites.size,
                playlists = backup.playlists.size,
                songs = backup.playlists.sumOf { it.songs.size }
            )
        } catch (_: Exception) {
            BackupInfo(exists = false)
        }
    }

    suspend fun export(): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val favorites = library.allFavoritesList()
            val playlists = library.allPlaylistsList()
            val songsByPlaylist = playlists.associate { pl ->
                pl.id to library.playlistSongsList(pl.id)
            }
            val backup = LibraryBackup(
                exportedAt = System.currentTimeMillis(),
                favorites = favorites.map { it.toBackupSong() },
                playlists = playlists.map { pl ->
                    BackupPlaylist(
                        name = pl.name, createdAt = pl.createdAt, updatedAt = pl.updatedAt,
                        songs = (songsByPlaylist[pl.id] ?: emptyList()).map { it.toBackupSong() }
                    )
                }
            )
            val json = encodeLibraryBackup(backup)
            writeBackupFile(json)
            Result.success(
                BackupInfo(
                    exists = true, exportedAt = backup.exportedAt,
                    sizeBytes = json.toByteArray().size.toLong(),
                    favorites = backup.favorites.size, playlists = backup.playlists.size,
                    songs = backup.playlists.sumOf { it.songs.size }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Merge-restore: favorites upserted, playlists matched by name (missing songs appended). */
    suspend fun restore(): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val uri = findBackupUri() ?: return@withContext Result.failure(
                IllegalStateException("No backup found in Documents/MasstamilanApp")
            )
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return@withContext Result.failure(IllegalStateException("Couldn't read backup"))
            val backup = decodeLibraryBackup(text).getOrThrow()
            var favCount = 0
            backup.favorites.forEach { song ->
                library.addFavorite(song.toQueueTrack())
                favCount++
            }
            var newSongs = 0
            backup.playlists.forEach { pl ->
                val entity: PlaylistEntity =
                    library.playlistByName(pl.name) ?: library.getPlaylist(
                        library.createPlaylist(pl.name)
                    ) ?: return@forEach
                val existing = library.playlistSongsList(entity.id)
                    .map { it.songKey }.toHashSet()
                pl.songs.forEach { song ->
                    val track = song.toQueueTrack()
                    if (library.favoriteKey(track) !in existing) {
                        library.addSongToPlaylist(entity.id, track)
                        newSongs++
                    }
                }
            }
            Result.success(
                BackupInfo(
                    exists = true, exportedAt = backup.exportedAt,
                    sizeBytes = text.toByteArray().size.toLong(),
                    favorites = favCount, playlists = backup.playlists.size, songs = newSongs
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT < 29) return@withContext legacyFile().delete()
            val uri = findBackupUri() ?: return@withContext false
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun writeBackupFile(json: String) {
        if (Build.VERSION.SDK_INT < 29) {
            val dest = legacyFile()
            dest.parentFile?.mkdirs()
            dest.writeText(json)
            return
        }
        // Replace any previous backup so exactly one file exists.
        findBackupUri()?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Files.FileColumns.MIME_TYPE, MIME)
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, "$RELATIVE_DIR/")
            put(MediaStore.Files.FileColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Files.getContentUri("external")
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Couldn't create backup file")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray())
        } ?: throw IllegalStateException("Couldn't write backup file")
        values.clear()
        values.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    /** Snapshot of the live library for the backup status line. */
    suspend fun liveCounts(): Triple<Int, Int, Int> {
        val favs = library.allFavoritesList().size
        val pls = library.allPlaylistsList()
        var songs = 0
        pls.forEach { runCatching { songs += library.playlistSongsList(it.id).size } }
        return Triple(favs, pls.size, songs)
    }
}
