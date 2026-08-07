package dev.pschmitt.jellyfin.di

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrClientConfig
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.pvr.PvrConfigResolver
import dev.pschmitt.jellyfin.repository.RadarrSearchRepository
import dev.pschmitt.jellyfin.repository.RadarrSearchRepositoryImpl
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.work.AutomaticSearchWorker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RadarrSearchModule {
    @Singleton
    @Provides
    fun provideRadarrSearchRepository(
        appPreferences: AppPreferences,
        pvrConfigResolver: PvrConfigResolver,
        workManager: WorkManager,
    ): RadarrSearchRepository {
        return RadarrSearchRepositoryImpl(
            appPreferences = appPreferences,
            resolveConfig = {
                pvrConfigResolver.resolveConfig(PvrService.RADARR)?.let {
                    PvrClientConfig(enabled = it.enabled, baseUrl = it.baseUrl, apiKey = it.apiKey)
                }
            },
            scheduleCompletionCheck = { movieId, commandId ->
                val request =
                    OneTimeWorkRequestBuilder<AutomaticSearchWorker>()
                        .setInputData(
                            workDataOf(
                                AutomaticSearchWorker.KEY_SOURCE to
                                    AutomaticSearchWorker.SOURCE_RADARR,
                                AutomaticSearchWorker.KEY_TARGET_ID to movieId,
                                AutomaticSearchWorker.KEY_COMMAND_ID to commandId,
                            )
                        )
                        .build()
                workManager.enqueue(request)
            },
        )
    }
}
