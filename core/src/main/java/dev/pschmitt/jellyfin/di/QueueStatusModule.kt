package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.repository.QueueStatusRepository
import dev.pschmitt.jellyfin.repository.QueueStatusRepositoryImpl
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.work.PvrDownloadFinishedNotifier
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
        jellyfinRepository: JellyfinRepository,
        secureCredentialStore: SecureCredentialStore,
        downloadFinishedNotifier: PvrDownloadFinishedNotifier,
    ): QueueStatusRepository {
        // Not tied to any Android component's lifecycle - the repository's poll loop should keep
        // running for as long as the process is alive, same rationale as WorkManagerModule
        // resolving a process-scoped WorkManager instance.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return QueueStatusRepositoryImpl(
            appPreferences = appPreferences,
            jellyfinRepository = jellyfinRepository,
            sonarrApiKeyProvider = {
                secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY)
            },
            radarrApiKeyProvider = {
                secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY)
            },
            onDownloadFinished = downloadFinishedNotifier::notifyFinished,
            scope = scope,
        )
    }
}
