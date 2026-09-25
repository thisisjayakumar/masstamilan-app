package com.masstamilan.app.feature.downloads

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.core.util.DownloadHelper
import com.masstamilan.app.data.model.DownloadEntity
import com.masstamilan.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(navController: NavController, downloadHelper: DownloadHelper? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloads by remember { mutableStateOf<List<DownloadEntity>>(emptyList()) }

    LaunchedEffect(downloadHelper) {
        downloads = downloadHelper?.getDownloadedFiles()?.map { file ->
            DownloadEntity(songName = file.nameWithoutExtension, filePath = file.absolutePath)
        } ?: emptyList()
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
                    items(downloads) { download ->
                        DownloadItem(download, navController)
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItem(download: DownloadEntity, navController: NavController) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Card)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Default.PlayArrow, "Play", tint = Primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(download.songName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(download.status, style = MaterialTheme.typography.labelSmall, color = TextHint)
            }
            IconButton(onClick = { /* delete */ }) {
                Icon(Icons.Default.Delete, "Delete", tint = Error, modifier = Modifier.size(20.dp))
            }
        }
    }
}
