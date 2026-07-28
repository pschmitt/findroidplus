package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.qrsetup.QrConfigManager
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object QrConfigModule {
    @Singleton
    @Provides
    fun provideQrConfigManager(
        serverDatabase: ServerDatabaseDao,
        appPreferences: AppPreferences,
        secureCredentialStore: SecureCredentialStore,
    ): QrConfigManager {
        return QrConfigManager(
            database = serverDatabase,
            appPreferences = appPreferences,
            getSecret = secureCredentialStore::getString,
            // Blocking, not the fire-and-forget default - see
            // SecureCredentialStore.putStringBlocking.
            putSecret = secureCredentialStore::putStringBlocking,
        )
    }
}
