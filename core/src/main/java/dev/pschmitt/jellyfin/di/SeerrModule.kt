package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.repository.SeerrRepository
import dev.pschmitt.jellyfin.repository.SeerrRepositoryImpl
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SeerrModule {
    @Singleton
    @Provides
    fun provideSeerrRepository(
        appPreferences: AppPreferences,
        secureCredentialStore: SecureCredentialStore,
    ): SeerrRepository {
        return SeerrRepositoryImpl(
            appPreferences = appPreferences,
            seerrApiKeyProvider = {
                secureCredentialStore.getString(PvrCredentialKeys.SEERR_API_KEY)
            },
        )
    }
}
