package com.masstamilan.app.feature.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.masstamilan.app.core.di.AppEntryPoint
import com.masstamilan.app.data.model.DownloadEntity
import com.masstamilan.app.ui.theme.Card
import com.masstamilan.app.ui.theme.Error
import com.masstamilan.app.ui.theme.Primary
import com.masstamilan.app.ui.theme.Surface
import com.masstamilan.app.ui.theme.TextHint
import com.masstamilan.app.ui.theme.TextPrimary
import com.masstamilan.app.ui.theme.TextSecondary
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(navController: NavController) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, AppEntryPoint::class.java
        )
    }
    val repository = remember { entryPoint.repository() }
    val playbackManager = remember { entryPoint.playbackManager() }
    val downloadHelper = remember { entryPoint.downloadHelper() }
    val scope = rememberCoroutineScope()

    val downloads by repository.getDownloads().collectAsState(initial = emptyList())
    var toast by remember { mutableStateOf<String?>(null) }

    fun playDownload(d: DownloadEntity) {
        if (d.status != "completed" || d.filePath.isBlank()) {
            toast = "Not downloaded yet"
            return
        }
        if (!downloadHelper.storedFileExists(d.filePath)) {
            toast = "File missing — please re-download \"${d.songName}\""
            return
        }
        if (playbackManager.playStream(context, d.filePath, d.songName, d.artist)) {
            navController.navigate("player/${d.songId}")
        } else {
            toast = "Couldn't play \"${d.songName}\""
        }
    }

    fun openWithExternal(d: DownloadEntity) {
        if (d.status != "completed" || d.filePath.isBlank()) {
            toast = "Not downloaded yet"
            return
        }
        if (!downloadHelper.storedFileExists(d.filePath)) {
            toast = "File missing — please re-download \"${d.songName}\""
            return
        }
        try {
            val uri = if (d.filePath.startsWith("content://")) {
                android.net.Uri.parse(d.filePath)
            } else {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    java.io.File(d.filePath)
                )
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/mpeg")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, "Play \"${d.songName}\" with…")
            )
        } catch (_: Exception) {
            toast = "No music player found to open \"${d.songName}\""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No downloads yet", color = TextHint)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(downloads, key = { it.id }) { download ->
                        DownloadItem(
                            download = download,
                            onPlay = { playDownload(download) },
                            onOpenWith = { openWithExternal(download) },
                            onDelete = {
                                scope.launch { repository.deleteDownload(download.id) }
                            }
                        )
                    }
                }
            }
            toast?.let {
                androidx.compose.runtime.LaunchedEffect(it) {
                    kotlinx.coroutines.delay(2500)
                    toast = null
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(it, color = TextSecondary, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
fun DownloadItem(
    download: DownloadEntity,
    onPlay: () -> Unit,
    onOpenWith: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Card)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay, enabled = download.status == "completed") {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Primary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(download.songName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        statusLine(download),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHint
                    )
                }
                if (download.status == "completed") {
                    IconButton(onClick = onOpenWith) {
                        Icon(Icons.Default.OpenInNew, "Open with local player", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = Error, modifier = Modifier.size(20.dp))
                }
            }
            if (download.status == "downloading") {
                LinearProgressIndicator(
                    progress = { download.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

private fun statusLine(d: DownloadEntity): String = when (d.status) {
    "completed" -> listOf(d.quality, "downloaded").filter { it.isNotBlank() }.joinToString(" • ")
    "downloading" -> "${(d.progress * 100).toInt()}% • ${d.quality}"
    "failed" -> "failed — tap download again to retry"
    else -> d.status.ifBlank { "pending" }
}
