package dev.pschmitt.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pschmitt.jellyfin.api.pvr.PvrClientConfig
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.pvr.PvrConfigResolver
import dev.pschmitt.jellyfin.repository.PvrDiskSpaceRepository
import dev.pschmitt.jellyfin.repository.PvrDiskSpaceRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PvrDiskSpaceModule {
    @Singleton
    @Provides
    fun providePvrDiskSpaceRepository(
        pvrConfigResolver: PvrConfigResolver
    ): PvrDiskSpaceRepository {
        return PvrDiskSpaceRepositoryImpl(
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
}
