package com.masstamilan.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masstamilan.app.core.settings.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferences
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
}
