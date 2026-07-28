package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrCredentialKeys
import dev.pschmitt.jellyfin.repository.CalendarCache
import dev.pschmitt.jellyfin.repository.CalendarRepository
import dev.pschmitt.jellyfin.repository.CalendarRepositoryImpl
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.repository.SeerrRepository
import dev.pschmitt.jellyfin.security.SecureCredentialStore
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {
    @Singleton
    @Provides
    fun provideCalendarRepository(
        appPreferences: AppPreferences,
        jellyfinRepository: JellyfinRepository,
        seerrRepository: SeerrRepository,
        secureCredentialStore: SecureCredentialStore,
    ): CalendarRepository {
        return CalendarRepositoryImpl(
            appPreferences = appPreferences,
            jellyfinRepository = jellyfinRepository,
            seerrRepository = seerrRepository,
            sonarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY) },
            radarrApiKeyProvider = { secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY) },
        )
    }

    @Singleton @Provides fun provideCalendarCache(): CalendarCache = CalendarCache()
}
