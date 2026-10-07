package com.masstamilan.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masstamilan.app.core.settings.UserPreferences
import com.masstamilan.app.data.repository.BackupInfo
import com.masstamilan.app.data.repository.LibraryBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val backup: LibraryBackupManager
) : ViewModel() {

    val streamHigh: StateFlow<Boolean> = prefs.streamHighQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val downloadHigh: StateFlow<Boolean> = prefs.downloadHighQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setStreamHigh(high: Boolean) {
        viewModelScope.launch { prefs.setStreamHighQuality(high) }
    }

    fun setDownloadHigh(high: Boolean) {
        viewModelScope.launch { prefs.setDownloadHighQuality(high) }
    }

    // ---- Library backup (single location: save / refetch / delete) ----
    private val _backupStatus = MutableStateFlow("Checking backup…")
    val backupStatus: StateFlow<String> = _backupStatus

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy

    init {
        refreshBackupStatus()
    }

    fun refreshBackupStatus() {
        viewModelScope.launch {
            val info = runCatching { backup.backupInfo() }.getOrDefault(BackupInfo(false))
            val (favs, pls, songs) = runCatching { backup.liveCounts() }.getOrDefault(Triple(0, 0, 0))
            _backupStatus.value = describe(info, favs, pls, songs)
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            _backupBusy.value = true
            _backupStatus.value = "Saving backup…"
            val result = runCatching { backup.export().getOrThrow() }
            _backupStatus.value = result.fold(
                onSuccess = {
                    "Saved ${it.favorites} liked + ${it.songs} playlist songs " +
                        "(${it.playlists} playlists) to Documents/MasstamilanApp."
                },
                onFailure = { "Export failed: ${it.message ?: "unknown error"}" }
            )
            _backupBusy.value = false
        }
    }

    fun importBackup() {
        viewModelScope.launch {
            _backupBusy.value = true
            _backupStatus.value = "Restoring backup…"
            val result = runCatching { backup.restore().getOrThrow() }
            _backupStatus.value = result.fold(
                onSuccess = {
                    "Restored ${it.favorites} liked + ${it.songs} new playlist songs " +
                        "(${it.playlists} playlists). Tap any song to re-resolve it live."
                },
                onFailure = { "Restore failed: ${it.message ?: "unknown error"}" }
            )
            _backupBusy.value = false
        }
    }

    fun deleteBackup() {
        viewModelScope.launch {
            _backupBusy.value = true
            val ok = runCatching { backup.delete() }.getOrDefault(false)
            _backupStatus.value = if (ok) {
                "Backup deleted from Documents/MasstamilanApp."
            } else {
                "No backup to delete."
            }
            _backupBusy.value = false
        }
    }

    private fun describe(info: BackupInfo, favs: Int, pls: Int, songs: Int): String {
        val live = "Library now: $favs liked • $pls playlists ($songs songs)."
        if (!info.exists) return "$live\nNo backup yet — export to survive reinstalls."
        val date = try {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(info.exportedAt))
        } catch (_: Exception) {
            ""
        }
        val kb = if (info.sizeBytes > 0) " • ${info.sizeBytes / 1024} KB" else ""
        return "$live\nBackup $date: ${info.favorites} liked • " +
            "${info.playlists} playlists (${info.songs} songs)$kb."
    }
}
