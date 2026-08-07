package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrClientConfig
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.pvr.PvrConfigResolver
import dev.pschmitt.jellyfin.repository.SeerrRepository
import dev.pschmitt.jellyfin.repository.SeerrRepositoryImpl
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SeerrModule {
    @Singleton
    @Provides
    fun provideSeerrRepository(
        appPreferences: AppPreferences,
        pvrConfigResolver: PvrConfigResolver,
    ): SeerrRepository {
        return SeerrRepositoryImpl(
            appPreferences = appPreferences,
            resolveConfig = {
                pvrConfigResolver.resolveConfig(PvrService.SEERR)?.let {
                    PvrClientConfig(enabled = it.enabled, baseUrl = it.baseUrl, apiKey = it.apiKey)
                }
            },
        )
    }
}
