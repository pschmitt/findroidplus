package dev.pschmitt.jellyfin.setup.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.JellyfinApi
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.setup.data.ProfileRepositoryImpl
import dev.pschmitt.jellyfin.setup.domain.ProfileRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProfileDataModule {
    @Singleton
    @Provides
    fun provideProfileRepository(
        jellyfinApi: JellyfinApi,
        serverDatabase: ServerDatabaseDao,
        appPreferences: AppPreferences,
        secureCredentialStore: SecureCredentialStore,
    ): ProfileRepository {
        return ProfileRepositoryImpl(
            jellyfinApi = jellyfinApi,
            dao = serverDatabase,
            appPreferences = appPreferences,
            secureCredentialStore = secureCredentialStore,
        )
    }
}
