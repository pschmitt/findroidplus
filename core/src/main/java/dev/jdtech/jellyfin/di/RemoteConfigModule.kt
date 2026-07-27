package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.RemoteConfigRepository
import dev.jdtech.jellyfin.repository.RemoteConfigRepositoryImpl
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
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
