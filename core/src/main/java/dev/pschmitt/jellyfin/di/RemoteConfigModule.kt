package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.repository.AutoDownloadRuleRepository
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.repository.RemoteConfigRepository
import dev.pschmitt.jellyfin.repository.RemoteConfigRepositoryImpl
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.utils.Downloader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemoteConfigModule {
    @Singleton
    @Provides
    fun provideRemoteConfigRepository(
        jellyfinRepository: JellyfinRepository,
        ruleRepository: AutoDownloadRuleRepository,
        appPreferences: AppPreferences,
        serverDatabase: ServerDatabaseDao,
        downloader: Downloader,
    ): RemoteConfigRepository {
        return RemoteConfigRepositoryImpl(
            jellyfinRepository,
            ruleRepository,
            appPreferences,
            serverDatabase,
            downloader,
        )
    }
}
