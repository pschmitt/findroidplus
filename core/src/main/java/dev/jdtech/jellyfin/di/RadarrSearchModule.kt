package dev.jdtech.jellyfin.di

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.api.pvr.PvrCredentialKeys
import dev.jdtech.jellyfin.repository.RadarrSearchRepository
import dev.jdtech.jellyfin.repository.RadarrSearchRepositoryImpl
import dev.jdtech.jellyfin.security.SecureCredentialStore
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.AutomaticSearchWorker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RadarrSearchModule {
    @Singleton
    @Provides
    fun provideRadarrSearchRepository(
        appPreferences: AppPreferences,
        secureCredentialStore: SecureCredentialStore,
        workManager: WorkManager,
    ): RadarrSearchRepository {
        return RadarrSearchRepositoryImpl(
            appPreferences = appPreferences,
            radarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY) },
            scheduleCompletionCheck = { movieId, commandId ->
                val request =
                    OneTimeWorkRequestBuilder<AutomaticSearchWorker>()
                        .setInputData(
                            workDataOf(
                                AutomaticSearchWorker.KEY_SOURCE to AutomaticSearchWorker.SOURCE_RADARR,
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
