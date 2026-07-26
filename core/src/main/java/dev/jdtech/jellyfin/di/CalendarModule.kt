package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.api.pvr.PvrCredentialKeys
import dev.jdtech.jellyfin.repository.CalendarCache
import dev.jdtech.jellyfin.repository.CalendarRepository
import dev.jdtech.jellyfin.repository.CalendarRepositoryImpl
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.SeerrRepository
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
