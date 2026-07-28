package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.repository.PvrDiskSpaceRepository
import dev.pschmitt.jellyfin.repository.PvrDiskSpaceRepositoryImpl
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PvrDiskSpaceModule {
    @Singleton
    @Provides
    fun providePvrDiskSpaceRepository(
        appPreferences: AppPreferences,
        secureCredentialStore: SecureCredentialStore,
    ): PvrDiskSpaceRepository {
        return PvrDiskSpaceRepositoryImpl(
            appPreferences = appPreferences,
            sonarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY) },
            radarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY) },
        )
    }
}
