package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrClientConfig
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.pvr.PvrConfigResolver
import dev.pschmitt.jellyfin.repository.CalendarCache
import dev.pschmitt.jellyfin.repository.CalendarRepository
import dev.pschmitt.jellyfin.repository.CalendarRepositoryImpl
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.repository.SeerrRepository
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
        pvrConfigResolver: PvrConfigResolver,
    ): CalendarRepository {
        return CalendarRepositoryImpl(
            appPreferences = appPreferences,
            jellyfinRepository = jellyfinRepository,
            seerrRepository = seerrRepository,
            resolveSonarrConfig = {
                pvrConfigResolver.resolveConfig(PvrService.SONARR)?.let {
                    PvrClientConfig(enabled = it.enabled, baseUrl = it.baseUrl, apiKey = it.apiKey)
                }
            },
            resolveRadarrConfig = {
                pvrConfigResolver.resolveConfig(PvrService.RADARR)?.let {
                    PvrClientConfig(enabled = it.enabled, baseUrl = it.baseUrl, apiKey = it.apiKey)
                }
            },
        )
    }

    @Singleton @Provides fun provideCalendarCache(): CalendarCache = CalendarCache()
}
