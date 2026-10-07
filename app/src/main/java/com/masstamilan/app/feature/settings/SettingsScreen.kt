package com.masstamilan.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.masstamilan.app.BuildConfig
import com.masstamilan.app.ui.theme.Card as CardColor
import com.masstamilan.app.ui.theme.Primary
import com.masstamilan.app.ui.theme.Surface
import com.masstamilan.app.ui.theme.TextHint
import com.masstamilan.app.ui.theme.TextPrimary
import com.masstamilan.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val streamHigh by viewModel.streamHigh.collectAsState()
    val downloadHigh by viewModel.downloadHigh.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    val backupBusy by viewModel.backupBusy.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Profile header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.padding(8.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("MassTamilan Listener", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Tamil songs • v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Audio quality", style = MaterialTheme.typography.titleSmall, color = TextSecondary)

            Spacer(Modifier.height(8.dp))
            QualityRow(
                title = "Streaming quality",
                subtitle = if (streamHigh) "High — 320kbps" else "Data saver — 128kbps",
                checked = streamHigh,
                onChecked = viewModel::setStreamHigh
            )
            QualityRow(
                title = "Download quality",
                subtitle = if (downloadHigh) "High — 320kbps" else "Data saver — 128kbps",
                checked = downloadHigh,
                onChecked = viewModel::setDownloadHigh
            )

            Spacer(Modifier.height(16.dp))
            Text("Library backup", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (backupBusy) {
                            CircularProgressIndicator(
                                color = Primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                        Text(
                            backupStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = viewModel::exportBackup,
                            enabled = !backupBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier.weight(1f)
                        ) { Text("Save", color = TextPrimary) }
                        OutlinedButton(
                            onClick = viewModel::importBackup,
                            enabled = !backupBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Restore", color = TextPrimary) }
                        TextButton(
                            onClick = viewModel::deleteBackup,
                            enabled = !backupBusy
                        ) { Text("Delete", color = TextSecondary) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Saved to Documents/MasstamilanApp — survives reinstalls. Metadata only, no audio.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHint
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("About", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Catalog and playback stream from masstamilan.dev. " +
                            "Download links expire and are re-resolved on play.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = TextHint)
                }
            }
        }
    }
}

@Composable
private fun QualityRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Primary,
                    checkedBorderColor = Primary,
                    uncheckedThumbColor = TextHint,
                    uncheckedTrackColor = Color.Transparent,
                    uncheckedBorderColor = TextHint
                )
            )
        }
    }
}
