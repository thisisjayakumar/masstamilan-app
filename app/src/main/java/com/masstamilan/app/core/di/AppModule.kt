package com.masstamilan.app.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.core.media.StreamResolver
import com.masstamilan.app.core.util.DownloadHelper
import com.masstamilan.app.data.dao.DownloadDao
import com.masstamilan.app.data.dao.FavoriteDao
import com.masstamilan.app.data.dao.PlaylistDao
import com.masstamilan.app.data.database.AppDatabase
import com.masstamilan.app.data.remote.MasstamilanApi
import com.masstamilan.app.data.repository.LibraryRepository
import com.masstamilan.app.data.repository.MasstamilanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideApi(client: OkHttpClient): MasstamilanApi {
        return MasstamilanApi(client)
    }

    @Provides
    @Singleton
    fun provideRepository(api: MasstamilanApi, database: AppDatabase): MasstamilanRepository {
        return MasstamilanRepository(api, database)
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): DownloadDao =
        database.downloadDao()

    @Provides
    @Singleton
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao =
        database.favoriteDao()

    @Provides
    @Singleton
    fun providePlaylistDao(database: AppDatabase): PlaylistDao =
        database.playlistDao()

    @Provides
    @Singleton
    fun provideLibraryRepository(
        favoriteDao: FavoriteDao,
        playlistDao: PlaylistDao
    ): LibraryRepository = LibraryRepository(favoriteDao, playlistDao)

    @Provides
    @Singleton
    fun provideDownloadHelper(
        client: OkHttpClient,
        api: MasstamilanApi,
        @ApplicationContext context: Context
    ): DownloadHelper = DownloadHelper(client, api, context)

    @Provides
    @Singleton
    fun provideStreamResolver(api: MasstamilanApi): StreamResolver =
        StreamResolver { pagePath -> api.resolveStreamUrl(pagePath) }

    @Provides
    @Singleton
    fun providePlaybackManager(resolver: StreamResolver): PlaybackManager {
        return PlaybackManager(resolver)
    }
}

/**
 * Shared entry point for @Composables that aren't under a Hilt ViewModel
 * (Home, SongDetail, Player). SearchScreen has its own equivalent.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AppEntryPoint {
    fun api(): MasstamilanApi
    fun repository(): MasstamilanRepository
    fun libraryRepository(): LibraryRepository
    fun downloadHelper(): DownloadHelper
    fun playbackManager(): PlaybackManager
}
