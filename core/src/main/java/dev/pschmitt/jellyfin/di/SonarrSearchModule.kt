package dev.pschmitt.jellyfin.di

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.repository.SonarrSearchRepository
import dev.pschmitt.jellyfin.repository.SonarrSearchRepositoryImpl
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.work.AutomaticSearchWorker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SonarrSearchModule {
    @Singleton
    @Provides
    fun provideSonarrSearchRepository(
        appPreferences: AppPreferences,
        secureCredentialStore: SecureCredentialStore,
        workManager: WorkManager,
    ): SonarrSearchRepository {
        return SonarrSearchRepositoryImpl(
            appPreferences = appPreferences,
            sonarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY) },
            scheduleCompletionCheck = { episodeId, commandId ->
                val request =
                    OneTimeWorkRequestBuilder<AutomaticSearchWorker>()
                        .setInputData(
                            workDataOf(
                                AutomaticSearchWorker.KEY_SOURCE to AutomaticSearchWorker.SOURCE_SONARR,
                                AutomaticSearchWorker.KEY_TARGET_ID to episodeId,
                                AutomaticSearchWorker.KEY_COMMAND_ID to commandId,
                            )
                        )
                        .build()
                workManager.enqueue(request)
            },
        )
    }
}
