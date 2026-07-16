package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.RadarrApi
import dev.jdtech.jellyfin.api.pvr.SonarrApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * [sonarrApiKeyProvider]/[radarrApiKeyProvider] resolve the current secret from
 * `SecureCredentialStore` - passed in as plain lambdas (rather than depending on
 * `SecureCredentialStore` directly) because that type lives in `core`, which depends on `data`,
 * not the other way around. Everything else this needs (`AppPreferences`, the PVR API clients,
 * `ServerDatabaseDao`) is already reachable from `data`.
 *
 * Constructed via [dev.jdtech.jellyfin.di.QueueStatusModule] (a Hilt `@Provides`, mirroring
 * `AutoDownloadRuleModule`) rather than an `@Inject` constructor, since `data` has no Hilt plugin.
 */
class QueueStatusRepositoryImpl(
    private val appPreferences: AppPreferences,
    private val serverDatabase: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val sonarrApiKeyProvider: () -> String?,
    private val radarrApiKeyProvider: () -> String?,
    private val scope: CoroutineScope,
) : QueueStatusRepository {

    private val _queueStatus = MutableStateFlow<Map<UUID, QueueStatus>>(emptyMap())
    private val refreshMutex = Mutex()
    private val pollingStarted = AtomicBoolean(false)

    override fun getQueueStatusFlow(): Flow<Map<UUID, QueueStatus>> =
        _queueStatus.onStart { ensurePollingStarted() }

    override fun getQueueStatusFlow(itemId: UUID): Flow<QueueStatus?> =
        getQueueStatusFlow().map { it[itemId] }.distinctUntilChanged()

    override suspend fun refreshNow() {
        // Serializes concurrent callers (poll loop, WorkManager backstop, a manual pull-to-refresh)
        // so two overlapping fetches can't race to publish a stale result after a fresher one.
        refreshMutex.withLock { _queueStatus.value = fetchQueueStatus() }
    }

    private fun ensurePollingStarted() {
        if (!pollingStarted.compareAndSet(false, true)) return
        scope.launch {
            while (isActive) {
                try {
                    refreshNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Queue status poll iteration failed")
                }
                val intervalMinutes =
                    appPreferences
                        .getValue(appPreferences.pvrPollIntervalMinutes)
                        .coerceAtLeast(MIN_POLL_INTERVAL_MINUTES)
                delay(intervalMinutes * 60_000L)
            }
        }
    }

    private suspend fun fetchQueueStatus(): Map<UUID, QueueStatus> = coroutineScope {
        // Each service is independently try/caught inside its own fetch function - a failure in
        // one must never blank out or crash the other's contribution to the merged map.
        val sonarrDeferred = async { fetchSonarrStatus() }
        val radarrDeferred = async { fetchRadarrStatus() }
        sonarrDeferred.await() + radarrDeferred.await()
    }

    private suspend fun fetchSonarrStatus(): Map<UUID, QueueStatus> {
        if (!appPreferences.getValue(appPreferences.sonarrEnabled)) return emptyMap()
        val baseUrl = appPreferences.getValue(appPreferences.sonarrBaseUrl)
        val apiKey = sonarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return emptyMap()

        return try {
            val api = SonarrApi(baseUrl, apiKey)
            val series = api.getSeries()
            val queue = api.getQueue()
            val (shows, episodesByShowId) = loadJellyfinShowsAndEpisodes()
            matchSonarr(series, queue, shows, episodesByShowId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh Sonarr queue status")
            emptyMap()
        }
    }

    private suspend fun fetchRadarrStatus(): Map<UUID, QueueStatus> {
        if (!appPreferences.getValue(appPreferences.radarrEnabled)) return emptyMap()
        val baseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl)
        val apiKey = radarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return emptyMap()

        return try {
            val api = RadarrApi(baseUrl, apiKey)
            val movies = api.getMovie()
            val queue = api.getQueue()
            val jellyfinMovies = loadJellyfinMovies()
            matchRadarr(movies, queue, jellyfinMovies)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh Radarr queue status")
            emptyMap()
        }
    }

    private suspend fun loadJellyfinShowsAndEpisodes():
        Pair<List<FindroidShow>, Map<UUID, List<FindroidEpisode>>> =
        withContext(Dispatchers.IO) {
            val serverId = appPreferences.getValue(appPreferences.currentServer)
            if (serverId == null) return@withContext emptyList<FindroidShow>() to emptyMap()
            val userId = jellyfinRepository.getUserId()

            val shows =
                serverDatabase.getShowsByServerId(serverId).map {
                    it.toFindroidShow(serverDatabase, userId)
                }
            val episodesByShowId =
                shows.associate { show ->
                    show.id to
                        serverDatabase.getEpisodesByShowId(show.id).map {
                            it.toFindroidEpisode(serverDatabase, userId)
                        }
                }
            shows to episodesByShowId
        }

    private suspend fun loadJellyfinMovies(): List<FindroidMovie> =
        withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext emptyList()
            val userId = jellyfinRepository.getUserId()
            serverDatabase.getMoviesByServerId(serverId).map {
                it.toFindroidMovie(serverDatabase, userId)
            }
        }

    private companion object {
        // Guards against a misconfigured 0 (or negative) pvrPollIntervalMinutes hammering
        // Sonarr/Radarr - the WorkManager backstop has its own, coarser floor (see
        // QueueStatusScheduler), since WorkManager itself enforces a 15-minute minimum.
        const val MIN_POLL_INTERVAL_MINUTES = 1
    }
}
