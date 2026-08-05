package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.api.pvr.PvrDiskSpaceDto
import dev.pschmitt.jellyfin.api.pvr.PvrRootFolderDto
import dev.pschmitt.jellyfin.api.pvr.RadarrApi
import dev.pschmitt.jellyfin.api.pvr.SonarrApi
import dev.pschmitt.jellyfin.models.PvrDiskSpaceResult
import dev.pschmitt.jellyfin.models.PvrServiceDiskSpace
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

/**
 * [sonarrApiKeyProvider]/[radarrApiKeyProvider] resolve the current secret from
 * `SecureCredentialStore` - passed in as plain lambdas rather than a direct dependency because that
 * type lives in `core`, which depends on `data`, not the other way around. Same pattern as
 * `CalendarRepositoryImpl`/`QueueStatusRepositoryImpl`.
 *
 * Constructed via [dev.pschmitt.jellyfin.di.PvrDiskSpaceModule] (a Hilt `@Provides`) rather than an
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
            val api = SonarrApi(baseUrl, apiKey)
            coroutineScope {
                val rootFoldersDeferred = async { api.getRootFolders() }
                val diskSpacesDeferred = async { api.getDiskSpace() }
                resolveStorage(rootFoldersDeferred.await(), diskSpacesDeferred.await())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Sonarr disk space")
            null
        }
    }

    private suspend fun fetchRadarr(): PvrServiceDiskSpace? {
        if (!appPreferences.getValue(appPreferences.radarrEnabled)) return null
        val baseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl)
        val apiKey = radarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return null

        return try {
            val api = RadarrApi(baseUrl, apiKey)
            coroutineScope {
                val rootFoldersDeferred = async { api.getRootFolders() }
                val diskSpacesDeferred = async { api.getDiskSpace() }
                resolveStorage(rootFoldersDeferred.await(), diskSpacesDeferred.await())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch Radarr disk space")
            null
        }
    }

    /**
     * /rootfolder only exposes the configured TV-shows/movies location(s) but no total space;
     * /diskspace has both numbers but for every mount point the host can see, not just the media
     * library. Match each root folder to the /diskspace entry whose path is its longest matching
     * prefix (the most specific mount actually containing it), then take the largest of those
     * matches as "the" root folder - a service can have more than one, commonly sharing the same
     * underlying volume, which summing would double-count. Falls back to the largest /diskspace
     * entry outright if no root folder matched anything (should not normally happen).
     */
    private fun resolveStorage(
        rootFolders: List<PvrRootFolderDto>,
        diskSpaces: List<PvrDiskSpaceDto>,
    ): PvrServiceDiskSpace? {
        if (diskSpaces.isEmpty()) return null
        val matched = rootFolders.mapNotNull { root ->
            diskSpaces.filter { root.path.startsWith(it.path) }.maxByOrNull { it.path.length }
        }
        val chosen =
            matched.maxByOrNull { it.totalSpace } ?: diskSpaces.maxByOrNull { it.totalSpace }
        return chosen?.let {
            PvrServiceDiskSpace(freeBytes = it.freeSpace, totalBytes = it.totalSpace)
        }
    }
}
