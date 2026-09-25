package com.masstamilan.app.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.masstamilan.app.core.media.PlaybackManager
import com.masstamilan.app.data.database.AppDatabase
import com.masstamilan.app.data.remote.MasstamilanApi
import com.masstamilan.app.data.repository.MasstamilanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

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
    fun provideOkHttpClient(): com.squareup.okhttp3.OkHttpClient {
        return com.squareup.okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideApi(client: com.squareup.okhttp3.OkHttpClient): MasstamilanApi {
        return MasstamilanApi(client)
    }

    @Provides
    @Singleton
    fun provideRepository(api: MasstamilanApi, database: AppDatabase): MasstamilanRepository {
        return MasstamilanRepository(api, database)
    }

    @Provides
    @Singleton
    fun providePlaybackManager(): PlaybackManager {
        return PlaybackManager()
    }
}
