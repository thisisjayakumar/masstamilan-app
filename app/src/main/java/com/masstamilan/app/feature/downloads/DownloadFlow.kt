package com.masstamilan.app.feature.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.masstamilan.app.core.util.DownloadHelper
import com.masstamilan.app.core.util.DownloadOption
import com.masstamilan.app.data.model.DownloadEntity
import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.model.songPagePathOf
import com.masstamilan.app.data.remote.MasstamilanApi
import com.masstamilan.app.data.repository.MasstamilanRepository
import com.masstamilan.app.ui.theme.TextPrimary
import com.masstamilan.app.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Always-shown quality picker: lists every quality found on the song page
 * (usually 128kbps + 320kbps) so the user chooses before downloading.
 */
@Composable
fun DownloadQualityDialog(
    songName: String,
    options: List<DownloadOption>?,
    selectedQuality: String?,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download quality") },
        text = {
            Column {
                Text(
                    songName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2
                )
                Spacer(Modifier.height(12.dp))
                if (options == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Fetching qualities…", color = TextSecondary)
                    }
                } else if (options.isEmpty()) {
                    Text("No download link for this song.", color = TextSecondary)
                } else {
                    options.forEach { opt ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(opt.quality) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuality == opt.quality,
                                onClick = { onSelect(opt.quality) }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(opt.quality, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = options?.any { it.quality == selectedQuality } == true
            ) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Resolve all download options for a song: song-page qualities preferred, direct link fallback. */
suspend fun resolveDownloadOptions(
    song: SongResult,
    downloadHelper: DownloadHelper
): List<DownloadOption> {
    val pagePath = songPagePathOf(song)
    if (pagePath.isNotBlank()) {
        val fromPage = downloadHelper.songQualities(pagePath)
        if (fromPage.isNotEmpty()) return fromPage
    }
    val dl = song.dlPath.trim()
    val direct = when {
        dl.startsWith("/downloader/") -> MasstamilanApi.BASE_URL + dl
        dl.startsWith("http") -> dl
        else -> null
    }
    if (direct != null) {
        val quality = if ("d320" in song.dlPath || "320" in song.dlPath) "320kbps" else "128kbps"
        return listOf(DownloadOption(quality = quality, url = direct))
    }
    return emptyList()
}

/**
 * Shared download execution: insert DB row, stream to MediaStore, mark
 * completed/failed, toast + navigate to Downloads on success.
 */
fun performChosenDownload(
    scope: CoroutineScope,
    repository: MasstamilanRepository,
    downloadHelper: DownloadHelper,
    navController: NavController,
    song: SongResult,
    option: DownloadOption,
    onToast: (String) -> Unit
) {
    scope.launch {
        onToast("Starting download: ${song.name} (${option.quality})")
        val rowId = repository.insertDownload(
            DownloadEntity(
                songId = song.id,
                songName = song.name,
                artist = song.artists,
                movieName = song.movieName,
                downloadUrl = option.url,
                quality = option.quality,
                status = "downloading"
            )
        )
        var lastPushed = 0f
        val result = downloadHelper.download(option.url, song.name, song.artists) { p, done, total ->
            if (p - lastPushed >= 0.05f || (total > 0 && done >= total)) {
                lastPushed = p
                scope.launch {
                    repository.updateProgress(rowId, "downloading", p, done, total)
                }
            }
        }
        result.fold(
            onSuccess = { playablePath ->
                scope.launch {
                    repository.markCompleted(rowId, playablePath)
                    onToast("Downloaded \"${song.name}\" (${option.quality})")
                    navController.navigate("downloads")
                }
            },
            onFailure = {
                scope.launch {
                    repository.markFailed(rowId)
                    onToast("Download failed — check connection")
                }
            }
        )
    }
}
