package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrClientConfigFull
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.profile.ProfileMigrationRunner
import dev.pschmitt.jellyfin.pvr.PvrConfigResolver
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
        pvrConfigResolver: PvrConfigResolver,
        secureCredentialStore: SecureCredentialStore,
        profileMigrationRunner: ProfileMigrationRunner,
    ): QrConfigManager {
        return QrConfigManager(
            database = serverDatabase,
            appPreferences = appPreferences,
            resolvePvrConfig = { service ->
                pvrConfigResolver.resolveConfig(service)?.let {
                    PvrClientConfigFull(
                        enabled = it.enabled,
                        baseUrl = it.baseUrl,
                        apiKey = it.apiKey,
                        httpHeaders = it.httpHeaders,
                        basicAuthUsername = it.basicAuthUsername,
                        basicAuthPassword = it.basicAuthPassword,
                    )
                }
            },
            // Blocking, not the fire-and-forget default - see
            // SecureCredentialStore.putStringBlocking.
            putSecret = secureCredentialStore::putStringBlocking,
            reconcileProfiles = profileMigrationRunner::reconcileAfterExternalRestore,
        )
    }
}
