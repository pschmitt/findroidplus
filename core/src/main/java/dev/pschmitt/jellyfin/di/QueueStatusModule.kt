package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrClientConfig
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.pvr.PvrConfigResolver
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.repository.QueueStatusRepository
import dev.pschmitt.jellyfin.repository.QueueStatusRepositoryImpl
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
        pvrConfigResolver: PvrConfigResolver,
        downloadFinishedNotifier: PvrDownloadFinishedNotifier,
    ): QueueStatusRepository {
        // Not tied to any Android component's lifecycle - the repository's poll loop should keep
        // running for as long as the process is alive, same rationale as WorkManagerModule
        // resolving a process-scoped WorkManager instance.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return QueueStatusRepositoryImpl(
            appPreferences = appPreferences,
            jellyfinRepository = jellyfinRepository,
            resolveSonarrConfig = {
                pvrConfigResolver.resolveConfig(PvrService.SONARR)?.let {
                    PvrClientConfig(enabled = it.enabled, baseUrl = it.baseUrl, apiKey = it.apiKey)
                }
            },
            resolveRadarrConfig = {
                pvrConfigResolver.resolveConfig(PvrService.RADARR)?.let {
                    PvrClientConfig(enabled = it.enabled, baseUrl = it.baseUrl, apiKey = it.apiKey)
                }
            },
            onDownloadFinished = downloadFinishedNotifier::notifyFinished,
            scope = scope,
        )
    }
}
