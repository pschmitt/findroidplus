package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.api.pvr.RadarrApi
import dev.jdtech.jellyfin.models.AutomaticSearchOutcome
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Movie counterpart of [SonarrSearchRepositoryImpl] - same lambda-injection pattern
 * ([radarrApiKeyProvider] resolves the secret from `SecureCredentialStore` in `core`,
 * [scheduleCompletionCheck] enqueues `dev.jdtech.jellyfin.work.AutomaticSearchWorker`) and the
 * same per-target release cache. Constructed via `dev.jdtech.jellyfin.di.RadarrSearchModule`
 * (a Hilt `@Provides`) rather than an `@Inject` constructor, since `data` has no Hilt plugin.
 */
class RadarrSearchRepositoryImpl(
    private val appPreferences: AppPreferences,
    private val radarrApiKeyProvider: () -> String?,
    private val scheduleCompletionCheck: (movieId: Int, commandId: Int) -> Unit,
) : RadarrSearchRepository {
    // Keyed by Radarr movie id. This repository is a Hilt singleton (see RadarrSearchModule), so
    // the cache lives for the app process, for as long as AppPreferences.pvrReleaseCacheMinutes
    // (Settings > Integrations) says - long enough to avoid re-hitting a slow indexer chain when
    // the user briefly re-opens the release picker for the same movie.
    private val releaseCache = ConcurrentHashMap<Int, CachedReleases>()

    private data class CachedReleases(val releases: List<PvrRelease>, val fetchedAtMs: Long)

    override suspend fun resolveMovieId(tmdbId: String): Int? {
        val api = api() ?: return null
        return api.getMovie().firstOrNull { it.tmdbId.toString() == tmdbId }?.id
    }

    override suspend fun searchMovie(movieId: Int): Result<Unit> {
        val result = runAction { it.searchMovie(movieId) }
        result.onSuccess { commandId ->
            // An automatic search may grab something new - drop any cached manual-search results
            // for this movie so a follow-up manual search reflects that.
            releaseCache.remove(movieId)
            scheduleCompletionCheck(movieId, commandId)
        }
        return result.map {}
    }

    override suspend fun awaitAutomaticSearchResult(
        movieId: Int,
        commandId: Int,
    ): Result<AutomaticSearchOutcome> = runAction { api ->
        var status = api.getCommandStatus(commandId)
        val deadline = System.currentTimeMillis() + PVR_COMMAND_AWAIT_TIMEOUT_MS
        while (status.status !in PVR_TERMINAL_COMMAND_STATUSES && System.currentTimeMillis() < deadline) {
            delay(PVR_COMMAND_POLL_INTERVAL_MS)
            status = api.getCommandStatus(commandId)
        }
        val movie = api.getMovieById(movieId)
        AutomaticSearchOutcome(
            succeeded = status.status == "completed",
            title = movie.title.ifBlank { "Movie #$movieId" },
        )
    }

    override suspend fun getReleases(movieId: Int): Result<List<PvrRelease>> {
        val cacheTtlMs = appPreferences.getValue(appPreferences.pvrReleaseCacheMinutes) * 60_000L
        releaseCache[movieId]?.let { cached ->
            if (System.currentTimeMillis() - cached.fetchedAtMs < cacheTtlMs) {
                return Result.success(cached.releases)
            }
        }
        val timeoutMs = appPreferences.getValue(appPreferences.pvrSearchTimeout)
        return runAction { it.getReleases(movieId, readTimeoutMs = timeoutMs) }
            .onSuccess { releases ->
                releaseCache[movieId] = CachedReleases(releases, System.currentTimeMillis())
            }
    }

    override suspend fun grabRelease(release: PvrRelease): Result<Unit> =
        runAction { it.grabRelease(release.guid, release.indexerId) }
            .onSuccess {
                // Don't know which movie this release belonged to from here - clear everything
                // rather than risk showing a stale list that still offers an already-grabbed release.
                releaseCache.clear()
            }

    private suspend fun <T> runAction(block: suspend (RadarrApi) -> T): Result<T> {
        val api = api() ?: return Result.failure(IllegalStateException("Radarr is not configured"))
        return try {
            Result.success(block(api))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Radarr search action failed")
            Result.failure(mapPvrSearchError("Radarr", e))
        }
    }

    private fun api(): RadarrApi? {
        if (!appPreferences.getValue(appPreferences.radarrEnabled)) return null
        val baseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl)
        val apiKey = radarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return null
        return RadarrApi(baseUrl, apiKey)
    }
}
