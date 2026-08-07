package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrClientConfig
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.pvr.PvrConfigResolver
import dev.pschmitt.jellyfin.repository.SeasonEpisodesRepository
import dev.pschmitt.jellyfin.repository.SeasonEpisodesRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SeasonEpisodesModule {
    @Singleton
    @Provides
    fun provideSeasonEpisodesRepository(
        pvrConfigResolver: PvrConfigResolver
    ): SeasonEpisodesRepository {
        return SeasonEpisodesRepositoryImpl(
            resolveConfig = {
                pvrConfigResolver.resolveConfig(PvrService.SONARR)?.let {
                    PvrClientConfig(enabled = it.enabled, baseUrl = it.baseUrl, apiKey = it.apiKey)
                }
            }
        )
    }
}
