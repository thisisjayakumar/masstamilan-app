package com.masstamilan.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore("user_prefs")

/** Persisted user settings (stream/download quality). Defaults to high. */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val STREAM_HIGH = booleanPreferencesKey("stream_high_quality")
        private val DOWNLOAD_HIGH = booleanPreferencesKey("download_high_quality")
    }

    val streamHighQuality: Flow<Boolean> =
        context.prefsDataStore.data.map { it[STREAM_HIGH] ?: true }

    val downloadHighQuality: Flow<Boolean> =
        context.prefsDataStore.data.map { it[DOWNLOAD_HIGH] ?: true }

    /** Suspend read for one-shot use (e.g. resolving a stream URL). */
    suspend fun preferHighQualityStream(): Boolean =
        context.prefsDataStore.data.map { it[STREAM_HIGH] ?: true }.first()

    suspend fun preferHighQualityDownload(): Boolean =
        context.prefsDataStore.data.map { it[DOWNLOAD_HIGH] ?: true }.first()

    suspend fun setStreamHighQuality(high: Boolean) {
        context.prefsDataStore.edit { it[STREAM_HIGH] = high }
    }

    suspend fun setDownloadHighQuality(high: Boolean) {
        context.prefsDataStore.edit { it[DOWNLOAD_HIGH] = high }
    }
}
