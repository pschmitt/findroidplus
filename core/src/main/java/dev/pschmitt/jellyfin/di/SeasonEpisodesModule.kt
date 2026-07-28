package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.repository.SeasonEpisodesRepository
import dev.pschmitt.jellyfin.repository.SeasonEpisodesRepositoryImpl
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SeasonEpisodesModule {
    @Singleton
    @Provides
    fun provideSeasonEpisodesRepository(
        appPreferences: AppPreferences,
        secureCredentialStore: SecureCredentialStore,
    ): SeasonEpisodesRepository {
        return SeasonEpisodesRepositoryImpl(
            appPreferences = appPreferences,
            sonarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY) },
        )
    }
}
