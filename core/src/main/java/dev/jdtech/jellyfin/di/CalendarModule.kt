package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.api.pvr.PvrCredentialKeys
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.CalendarRepository
import dev.jdtech.jellyfin.repository.CalendarRepositoryImpl
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.security.SecureCredentialStore
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {
    @Singleton
    @Provides
    fun provideCalendarRepository(
        appPreferences: AppPreferences,
        serverDatabase: ServerDatabaseDao,
        jellyfinRepository: JellyfinRepository,
        secureCredentialStore: SecureCredentialStore,
    ): CalendarRepository {
        return CalendarRepositoryImpl(
            appPreferences = appPreferences,
            serverDatabase = serverDatabase,
            jellyfinRepository = jellyfinRepository,
            sonarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY) },
            radarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY) },
        )
    }
}
