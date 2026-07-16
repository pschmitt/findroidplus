package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.api.pvr.PvrCredentialKeys
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import dev.jdtech.jellyfin.repository.QueueStatusRepositoryImpl
import dev.jdtech.jellyfin.security.SecureCredentialStore
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object QueueStatusModule {
    @Singleton
    @Provides
    fun provideQueueStatusRepository(
        appPreferences: AppPreferences,
        serverDatabase: ServerDatabaseDao,
        jellyfinRepository: JellyfinRepository,
        secureCredentialStore: SecureCredentialStore,
    ): QueueStatusRepository {
        // Not tied to any Android component's lifecycle - the repository's poll loop should keep
        // running for as long as the process is alive, same rationale as WorkManagerModule
        // resolving a process-scoped WorkManager instance.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return QueueStatusRepositoryImpl(
            appPreferences = appPreferences,
            serverDatabase = serverDatabase,
            jellyfinRepository = jellyfinRepository,
            sonarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY) },
            radarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY) },
            scope = scope,
        )
    }
}
