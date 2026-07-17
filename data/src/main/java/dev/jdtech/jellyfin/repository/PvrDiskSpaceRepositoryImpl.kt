package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.PvrRootFolderDto
import dev.jdtech.jellyfin.api.pvr.RadarrApi
import dev.jdtech.jellyfin.api.pvr.SonarrApi
import dev.jdtech.jellyfin.models.PvrDiskSpaceResult
import dev.jdtech.jellyfin.models.PvrServiceDiskSpace
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * [sonarrApiKeyProvider]/[radarrApiKeyProvider] resolve the current secret from
 * `SecureCredentialStore` - passed in as plain lambdas rather than a direct dependency because
 * that type lives in `core`, which depends on `data`, not the other way around. Same pattern as
 * `CalendarRepositoryImpl`/`QueueStatusRepositoryImpl`.
 *
 * Constructed via [dev.jdtech.jellyfin.di.PvrDiskSpaceModule] (a Hilt `@Provides`) rather than an
 * `@Inject` constructor, since `data` has no Hilt plugin.
 */
class PvrDiskSpaceRepositoryImpl(
    private val appPreferences: AppPreferences,
    private val sonarrApiKeyProvider: () -> String?,
    private val radarrApiKeyProvider: () -> String?,
) : PvrDiskSpaceRepository {

    override suspend fun getDiskSpace(): PvrDiskSpaceResult = coroutineScope {
        val sonarrDeferred = async { fetchSonarr() }
        val radarrDeferred = async { fetchRadarr() }
        // Assume Sonarr/Radarr share the same disk (see PvrDiskSpaceResult) - only surface one.
        PvrDiskSpaceResult(storage = sonarrDeferred.await() ?: radarrDeferred.await())
    }

    private suspend fun fetchSonarr(): PvrServiceDiskSpace? {
        if (!appPreferences.getValue(appPreferences.sonarrEnabled)) return null
        val baseUrl = appPreferences.getValue(appPreferences.sonarrBaseUrl)
        val apiKey = sonarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return null

        return try {
            SonarrApi(baseUrl, apiKey).getRootFolders().toServiceDiskSpace()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Sonarr root folder disk space")
            null
        }
    }

    private suspend fun fetchRadarr(): PvrServiceDiskSpace? {
        if (!appPreferences.getValue(appPreferences.radarrEnabled)) return null
        val baseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl)
        val apiKey = radarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return null

        return try {
            RadarrApi(baseUrl, apiKey).getRootFolders().toServiceDiskSpace()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Radarr root folder disk space")
            null
        }
    }

    // The largest configured root folder, not a sum across all of them - a service can have more
    // than one root folder sharing the same underlying volume (see PvrServiceDiskSpace), which
    // summing would double-count. The largest one is taken as "the" root folder rather than the
    // first, since load order isn't guaranteed to put the main library first.
    private fun List<PvrRootFolderDto>.toServiceDiskSpace(): PvrServiceDiskSpace? =
        maxByOrNull { it.totalSpace }
            ?.let { PvrServiceDiskSpace(freeBytes = it.freeSpace, totalBytes = it.totalSpace) }
}
