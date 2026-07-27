package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.qrsetup.QrConfigManager
import dev.jdtech.jellyfin.security.SecureCredentialStore
import dev.jdtech.jellyfin.settings.domain.AppPreferences
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
