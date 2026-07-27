package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.RadarrApi
import dev.jdtech.jellyfin.api.pvr.RadarrManualImportFile
import dev.jdtech.jellyfin.api.pvr.SonarrApi
import dev.jdtech.jellyfin.api.pvr.SonarrManualImportFile
import dev.jdtech.jellyfin.api.pvr.SonarrQueueItem
import dev.jdtech.jellyfin.api.pvr.SonarrSeries
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.ManualImportCandidate
import dev.jdtech.jellyfin.models.PvrFetchError
import dev.jdtech.jellyfin.models.PvrQueueEntry
import dev.jdtech.jellyfin.models.PvrQueueSnapshot
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.QueueItemStatus
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

/**
 * [sonarrApiKeyProvider]/[radarrApiKeyProvider] resolve the current secret from
 * `SecureCredentialStore` - passed in as plain lambdas (rather than depending on
 * `SecureCredentialStore` directly) because that type lives in `core`, which depends on `data`,
 * not the other way around. [onDownloadFinished] posts the "download finished" notification -
 * also a lambda, since notifications are a `core`-layer concern.
 *
 * Constructed via [dev.jdtech.jellyfin.di.QueueStatusModule] (a Hilt `@Provides`, mirroring
 * `AutoDownloadRuleModule`) rather than an `@Inject` constructor, since `data` has no Hilt plugin.
 *
 * Match candidates are fetched from the *live* Jellyfin library via [JellyfinRepository] rather
 * than the local Room cache (which only holds downloaded items) - same reasoning as
 * `CalendarRepositoryImpl`, which hit this exact bug first. To keep the per-poll request count
 * bounded, only shows/seasons actually referenced by the current queue get their episodes
 * fetched.
 */
class QueueStatusRepositoryImpl(
    private val appPreferences: AppPreferences,
    private val jellyfinRepository: JellyfinRepository,
    private val sonarrApiKeyProvider: () -> String?,
    private val radarrApiKeyProvider: () -> String?,
    private val onDownloadFinished: (title: String) -> Unit,
    private val scope: CoroutineScope,
) : QueueStatusRepository {

    private val _queueSnapshot = MutableStateFlow(PvrQueueSnapshot())
    private val refreshMutex = Mutex()
    private val pollingStarted = AtomicBoolean(false)

    // Whether _queueSnapshot holds a real poll result yet - the initial empty snapshot must not
    // be diffed against (nothing has "disappeared" before the first fetch).
    private var hasPolledOnce = false

    // How many polls in a row have failed for each service, and what its queue looked like the
    // last time a poll actually succeeded - see fetchSonarrSnapshot()/fetchRadarrSnapshot() for
    // why: a single blip (Sonarr momentarily busy, a transient network hiccup) shouldn't flash an
    // error banner and wipe the on-screen queue instantly. These are only ever touched by their
    // own service's fetch function, and consecutive polls are serialized by refreshMutex, so
    // there's no concurrent access to guard against.
    private var sonarrConsecutiveFailures = 0
    private var radarrConsecutiveFailures = 0
    private var lastGoodSonarrEntries: List<PvrQueueEntry> = emptyList()
    private var lastGoodRadarrEntries: List<PvrQueueEntry> = emptyList()

    override fun getQueueSnapshotFlow(): Flow<PvrQueueSnapshot> =
        _queueSnapshot.onStart { ensurePollingStarted() }

    override fun getQueueStatusFlow(): Flow<Map<UUID, QueueStatus>> =
        getQueueSnapshotFlow().map { it.entries.toQueueStatusMap() }.distinctUntilChanged()

    override fun getQueueStatusFlow(itemId: UUID): Flow<QueueStatus?> =
        getQueueStatusFlow().map { it[itemId] }.distinctUntilChanged()

    override fun getRadarrQueueStatusFlow(): Flow<Map<Int, QueueStatus>> =
        getQueueSnapshotFlow().map { it.entries.toRadarrQueueStatusMap() }.distinctUntilChanged()

    override fun getSonarrQueueStatusFlow(): Flow<Map<Int, QueueStatus>> =
        getQueueSnapshotFlow().map { it.entries.toSonarrQueueStatusMap() }.distinctUntilChanged()

    override fun getQueueEntriesFlow(itemId: UUID): Flow<List<PvrQueueEntry>> =
        getQueueSnapshotFlow()
            .map { snapshot ->
                val anchor = snapshot.entries.firstOrNull { it.item?.id == itemId }
                if (anchor == null) {
                    emptyList()
                } else {
                    val key = anchor.duplicateGroupKey()
                    if (key == null) listOf(anchor) else snapshot.entries.filter { it.duplicateGroupKey() == key }
                }
            }
            .distinctUntilChanged()

    override fun getQueueEntriesFlow(
        source: PvrSource,
        tmdbId: Int?,
        sonarrEpisodeId: Int?,
    ): Flow<List<PvrQueueEntry>> =
        getQueueSnapshotFlow()
            .map { snapshot ->
                snapshot.entries.filter { entry ->
                    entry.status.source == source &&
                        ((tmdbId != null && entry.tmdbId == tmdbId) ||
                            (sonarrEpisodeId != null && entry.sonarrEpisodeId == sonarrEpisodeId))
                }
            }
            .distinctUntilChanged()

    override suspend fun refreshNow() {
        // Serializes concurrent callers (poll loop, WorkManager backstop, a manual pull-to-refresh)
        // so two overlapping fetches can't race to publish a stale result after a fresher one.
        refreshMutex.withLock {
            val snapshot = fetchQueueSnapshot()
            if (hasPolledOnce) notifyFinishedDownloads(_queueSnapshot.value, snapshot)
            hasPolledOnce = true
            _queueSnapshot.value = snapshot
        }
    }

    override suspend fun removeQueueItem(
        source: PvrSource,
        queueItemId: Int,
        removeFromClient: Boolean,
        blocklist: Boolean,
    ): Result<Unit> {
        val serviceName = serviceName(source)
        return try {
            deleteQueueItemRemote(source, queueItemId, removeFromClient, blocklist)
            // Drop the entry from the current snapshot before refreshing: the disappearance diff
            // in notifyFinishedDownloads would otherwise read this user-initiated removal as
            // "finished importing" and fire a bogus notification. The refresh then updates the
            // flows right away instead of waiting out the poll interval.
            dropFromSnapshot { it.status.source == source && it.queueItemId == queueItemId }
            refreshNow()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to remove $serviceName queue item $queueItemId")
            Result.failure(mapPvrSearchError(serviceName, e))
        }
    }

    override suspend fun removeQueueItems(
        items: List<Pair<PvrSource, Int>>,
        removeFromClient: Boolean,
        blocklist: Boolean,
    ): List<Pair<PvrSource, Int>> = coroutineScope {
        if (items.isEmpty()) return@coroutineScope emptyList()

        // Each removal is its own request/failure domain (no bulk queue-delete endpoint exists),
        // run concurrently rather than sequentially so an N-item removal doesn't serialize N
        // network round trips - only the final snapshot filter + refresh below is shared.
        val failed =
            items
                .map { (source, queueItemId) ->
                    async {
                        try {
                            deleteQueueItemRemote(source, queueItemId, removeFromClient, blocklist)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to remove ${serviceName(source)} queue item $queueItemId")
                            source to queueItemId
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()

        val removed = (items - failed.toSet()).toSet()
        dropFromSnapshot { (it.status.source to it.queueItemId) in removed }
        refreshNow()
        failed
    }

    private suspend fun deleteQueueItemRemote(
        source: PvrSource,
        queueItemId: Int,
        removeFromClient: Boolean,
        blocklist: Boolean,
    ) {
        when (source) {
            PvrSource.SONARR ->
                sonarrApiOrThrow().deleteQueueItem(queueItemId, removeFromClient, blocklist)
            PvrSource.RADARR ->
                radarrApiOrThrow().deleteQueueItem(queueItemId, removeFromClient, blocklist)
        }
    }

    override suspend fun getManualImportCandidates(
        source: PvrSource,
        downloadId: String,
    ): Result<List<ManualImportCandidate>> {
        val serviceName = serviceName(source)
        return try {
            val candidates =
                when (source) {
                    PvrSource.SONARR ->
                        sonarrApiOrThrow().getManualImportItems(downloadId).map { it.toCandidate() }
                    PvrSource.RADARR ->
                        radarrApiOrThrow().getManualImportItems(downloadId).map { it.toCandidate() }
                }
            Result.success(candidates)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to list $serviceName manual import candidates for $downloadId")
            Result.failure(mapPvrSearchError(serviceName, e))
        }
    }

    override suspend fun performManualImport(
        source: PvrSource,
        downloadId: String,
        selectedIds: Set<Int>,
    ): Result<Unit> {
        val serviceName = serviceName(source)
        return try {
            when (source) {
                PvrSource.SONARR -> {
                    val api = sonarrApiOrThrow()
                    val files =
                        api.getManualImportItems(downloadId)
                            .filter { it.id in selectedIds }
                            .map { item ->
                                SonarrManualImportFile(
                                    id = item.id,
                                    path = item.path,
                                    folderName = item.folderName,
                                    seriesId = item.series?.id,
                                    episodeIds = item.episodes.map { it.id },
                                    quality = item.quality,
                                    languages = item.languages,
                                    downloadId = item.downloadId,
                                )
                            }
                    api.triggerManualImport(files)
                }
                PvrSource.RADARR -> {
                    val api = radarrApiOrThrow()
                    val files =
                        api.getManualImportItems(downloadId)
                            .filter { it.id in selectedIds }
                            .map { item ->
                                RadarrManualImportFile(
                                    id = item.id,
                                    path = item.path,
                                    folderName = item.folderName,
                                    movieId = item.movie?.id,
                                    quality = item.quality,
                                    languages = item.languages,
                                    downloadId = item.downloadId,
                                )
                            }
                    api.triggerManualImport(files)
                }
            }
            refreshNow()
            // Sonarr/Radarr's import command is async - the HTTP call above only means it was
            // accepted, not that the file has actually landed on disk yet. Wait for this entry to
            // actually leave the queue (Sonarr/Radarr remove the row once the import genuinely
            // finishes) before telling Jellyfin to scan, rather than racing a scan against a file
            // that isn't there yet. Fire-and-forget on the repository's own scope - the caller
            // (the manage-import sheet) has already gotten its success result and moved on.
            scope.launch { awaitImportThenRefreshLibrary(source, downloadId) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to perform $serviceName manual import for $downloadId")
            Result.failure(mapPvrSearchError(serviceName, e))
        }
    }

    private suspend fun awaitImportThenRefreshLibrary(source: PvrSource, downloadId: String) {
        repeat(IMPORT_POLL_MAX_ATTEMPTS) { attempt ->
            delay(IMPORT_POLL_INTERVAL_MS)
            refreshNow()
            val stillQueued =
                _queueSnapshot.value.entries.any {
                    it.status.source == source && it.status.downloadId == downloadId
                }
            if (!stillQueued) return jellyfinRepository.refreshLibrary()
        }
        // Gave up waiting - scan anyway rather than never telling Jellyfin about a real import.
        jellyfinRepository.refreshLibrary()
    }

    private suspend fun dropFromSnapshot(predicate: (PvrQueueEntry) -> Boolean) {
        refreshMutex.withLock {
            _queueSnapshot.value =
                _queueSnapshot.value.copy(
                    entries = _queueSnapshot.value.entries.filterNot(predicate)
                )
        }
    }

    private suspend fun sonarrApiOrThrow(): SonarrApi {
        val baseUrl = appPreferences.getValue(appPreferences.sonarrBaseUrl)
        val apiKey = sonarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
            throw IllegalStateException("Sonarr is not configured")
        }
        return SonarrApi(baseUrl, apiKey)
    }

    private suspend fun radarrApiOrThrow(): RadarrApi {
        val baseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl)
        val apiKey = radarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
            throw IllegalStateException("Radarr is not configured")
        }
        return RadarrApi(baseUrl, apiKey)
    }

    private fun serviceName(source: PvrSource): String =
        if (source == PvrSource.SONARR) "Sonarr" else "Radarr"

    /**
     * A queue entry that was queued/downloading/importing in the [previous] snapshot and is gone
     * from the [new] one has (in the overwhelmingly common case) finished importing - Sonarr/Radarr
     * remove entries from the queue once the file is in place. Disappearance means nothing when the
     * service wasn't successfully fetched this poll (disabled, or the fetch errored), so those
     * sources are skipped. Failed/warning entries are also skipped - those get removed by the user,
     * not by a successful import.
     */
    private fun notifyFinishedDownloads(previous: PvrQueueSnapshot, new: PvrQueueSnapshot) {
        val newKeys = new.entries.map { it.status.source to it.queueItemId }.toSet()
        previous.entries
            .filter { it.status.source in new.fetchedSources }
            .filter { (it.status.source to it.queueItemId) !in newKeys }
            .filter { it.status.status in ACTIVE_STATUSES }
            .forEach { entry ->
                Timber.d("PVR queue entry finished: ${entry.title}")
                onDownloadFinished(entry.title)
            }
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

    private suspend fun fetchQueueSnapshot(): PvrQueueSnapshot = coroutineScope {
        // Each service is independently try/caught inside its own fetch function - a failure in
        // one must never blank out or crash the other's contribution to the merged snapshot.
        val sonarrDeferred = async { fetchSonarrSnapshot() }
        val radarrDeferred = async { fetchRadarrSnapshot() }
        val sonarr = sonarrDeferred.await()
        val radarr = radarrDeferred.await()
        PvrQueueSnapshot(
            entries = sonarr.entries + radarr.entries,
            errors = sonarr.errors + radarr.errors,
            fetchedSources = sonarr.fetchedSources + radarr.fetchedSources,
        )
    }

    private suspend fun fetchSonarrSnapshot(): PvrQueueSnapshot {
        if (!appPreferences.getValue(appPreferences.sonarrEnabled)) return PvrQueueSnapshot()
        val baseUrl = appPreferences.getValue(appPreferences.sonarrBaseUrl)
        val apiKey = sonarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return PvrQueueSnapshot()

        return try {
            val entries = retryOnce {
                val api = SonarrApi(baseUrl, apiKey)
                val queue = api.getQueue()
                if (queue.isEmpty()) {
                    emptyList()
                } else {
                    val series = api.getSeries()
                    val (shows, episodesByShowId) = loadQueueReferencedShowsAndEpisodes(series, queue)
                    matchSonarr(series, queue, shows, episodesByShowId)
                }
            }
            sonarrConsecutiveFailures = 0
            lastGoodSonarrEntries = entries
            PvrQueueSnapshot(entries = entries, fetchedSources = setOf(PvrSource.SONARR))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sonarrConsecutiveFailures++
            Timber.w(e, "Failed to refresh Sonarr queue status (%d in a row)", sonarrConsecutiveFailures)
            // A blip that's still within tolerance keeps showing the last known-good queue
            // instead of flashing an error banner and wiping the list after a single failed poll
            // - fetchedSources stays empty so notifyFinishedDownloads doesn't diff against this
            // reused data and misfire a "finished" notification.
            if (sonarrConsecutiveFailures < FAILURE_THRESHOLD) {
                PvrQueueSnapshot(entries = lastGoodSonarrEntries)
            } else {
                PvrQueueSnapshot(errors = listOf(fetchError(PvrSource.SONARR, "Sonarr", e)))
            }
        }
    }

    private suspend fun fetchRadarrSnapshot(): PvrQueueSnapshot {
        if (!appPreferences.getValue(appPreferences.radarrEnabled)) return PvrQueueSnapshot()
        val baseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl)
        val apiKey = radarrApiKeyProvider()
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) return PvrQueueSnapshot()

        return try {
            val entries = retryOnce {
                val api = RadarrApi(baseUrl, apiKey)
                val queue = api.getQueue()
                if (queue.isEmpty()) {
                    emptyList()
                } else {
                    matchRadarr(api.getMovie(), queue, loadJellyfinMovies())
                }
            }
            radarrConsecutiveFailures = 0
            lastGoodRadarrEntries = entries
            PvrQueueSnapshot(entries = entries, fetchedSources = setOf(PvrSource.RADARR))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            radarrConsecutiveFailures++
            Timber.w(e, "Failed to refresh Radarr queue status (%d in a row)", radarrConsecutiveFailures)
            if (radarrConsecutiveFailures < FAILURE_THRESHOLD) {
                PvrQueueSnapshot(entries = lastGoodRadarrEntries)
            } else {
                PvrQueueSnapshot(errors = listOf(fetchError(PvrSource.RADARR, "Radarr", e)))
            }
        }
    }

    /**
     * Retries [block] once after a short delay on any failure - absorbs a brief network hiccup
     * within the same poll instead of waiting out a whole extra poll cycle (which, combined with
     * the consecutive-failure tolerance above, is what actually keeps the error banner from
     * appearing too eagerly).
     */
    private suspend fun <T> retryOnce(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "PVR fetch failed, retrying once")
            delay(RETRY_DELAY_MS)
            block()
        }
    }

    /**
     * Fetches the Jellyfin shows and episodes the current [queue] can possibly match against:
     * the full show list is one request, but episodes are fetched only for the shows *and
     * seasons* the queue references, since episode listing is one request per season and a
     * long-running show can have dozens of seasons irrelevant to the queue.
     */
    private suspend fun loadQueueReferencedShowsAndEpisodes(
        series: List<SonarrSeries>,
        queue: List<SonarrQueueItem>,
    ): Pair<List<FindroidShow>, Map<UUID, List<FindroidEpisode>>> = coroutineScope {
        val tvdbIdBySeriesId: Map<Int, String> =
            series.filter { it.tvdbId != 0 }.associate { it.id to it.tvdbId.toString() }
        val seasonNumbersByTvdbId: Map<String, Set<Int>> =
            queue
                .mapNotNull { item -> tvdbIdBySeriesId[item.seriesId]?.let { it to item.seasonNumber } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, seasonNumbers) -> seasonNumbers.toSet() }
        if (seasonNumbersByTvdbId.isEmpty()) {
            return@coroutineScope emptyList<FindroidShow>() to emptyMap()
        }

        val shows =
            jellyfinRepository
                .getItems(includeTypes = listOf(BaseItemKind.SERIES), recursive = true)
                .filterIsInstance<FindroidShow>()
                .filter { it.tvdbId in seasonNumbersByTvdbId.keys }

        val episodesByShowId =
            shows
                .map { show ->
                    async {
                        val queuedSeasonNumbers = seasonNumbersByTvdbId[show.tvdbId].orEmpty()
                        val episodes =
                            jellyfinRepository
                                .getSeasons(show.id)
                                .filter { it.indexNumber in queuedSeasonNumbers }
                                .map { season ->
                                    async {
                                        jellyfinRepository.getEpisodes(
                                            seriesId = show.id,
                                            seasonId = season.id,
                                        )
                                    }
                                }
                                .awaitAll()
                                .flatten()
                        show.id to episodes
                    }
                }
                .awaitAll()
                .toMap()
        shows to episodesByShowId
    }

    private suspend fun loadJellyfinMovies(): List<FindroidMovie> =
        jellyfinRepository
            .getItems(includeTypes = listOf(BaseItemKind.MOVIE), recursive = true)
            .filterIsInstance<FindroidMovie>()

    private fun fetchError(source: PvrSource, serviceName: String, e: Exception): PvrFetchError =
        PvrFetchError(
            source = source,
            message = mapPvrSearchError(serviceName, e).message ?: "$serviceName request failed",
        )

    private companion object {
        // Guards against a misconfigured 0 (or negative) pvrPollIntervalMinutes hammering
        // Sonarr/Radarr - the WorkManager backstop has its own, coarser floor (see
        // QueueStatusScheduler), since WorkManager itself enforces a 15-minute minimum.
        const val MIN_POLL_INTERVAL_MINUTES = 1

        // How many polls in a row must fail before a service is actually reported as unavailable
        // - see fetchSonarrSnapshot()/fetchRadarrSnapshot(). With the Downloads screen's 10s
        // foreground poll cadence, that's ~20-30s of tolerance on top of retryOnce()'s own delay
        // before the error banner appears.
        const val FAILURE_THRESHOLD = 3
        const val RETRY_DELAY_MS = 2_000L

        // How long/often to poll for a manual import to actually finish (see
        // awaitImportThenRefreshLibrary) before giving up and scanning anyway - 6 * 2s = 12s of
        // patience, comfortably past the time Sonarr/Radarr's own import command usually takes.
        const val IMPORT_POLL_MAX_ATTEMPTS = 6
        const val IMPORT_POLL_INTERVAL_MS = 2_000L

        val ACTIVE_STATUSES =
            setOf(QueueItemStatus.QUEUED, QueueItemStatus.DOWNLOADING, QueueItemStatus.IMPORTING)
    }
}
