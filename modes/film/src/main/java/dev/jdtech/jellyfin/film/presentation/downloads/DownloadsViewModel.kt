package dev.jdtech.jellyfin.film.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.PvrQueueEntry
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.models.toFindroidEpisodes
import dev.jdtech.jellyfin.models.toFindroidMovies
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.PvrDiskSpaceRepository
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val autoDownloadRuleRepository: AutoDownloadRuleRepository,
    private val appPreferences: AppPreferences,
    private val queueStatusRepository: QueueStatusRepository,
    private val pvrDiskSpaceRepository: PvrDiskSpaceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadsState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<DownloadsEvent>()
    val events = eventsChannel.receiveAsFlow()

    // itemId (movie or episode id) -> downloadId for every item currently being tracked by
    // progressJobs, so pause/resume/cancel can turn an item id from the UI into the downloadId
    // the Downloader API needs.
    private val downloadIdsByItem = mutableMapOf<UUID, Long>()
    private val progressJobs = mutableMapOf<UUID, Job>()
    private var refreshJob: Job? = null
    private var pvrRefreshJob: Job? = null

    fun startObserving() {
        // Unlike the one-shot registrations below, re-run every time the screen is (re-)entered,
        // not just the first time this ViewModel instance observes anything - the ViewModel (and
        // refreshJob) can outlive a single visit to this screen (e.g. surviving a tab switch), in
        // which case the early return below would otherwise skip this forever after the first visit.
        refreshStorage()
        if (refreshJob != null) return
        refreshJob =
            viewModelScope.launch {
                while (isActive) {
                    refreshDownloads()
                    delay(REFRESH_INTERVAL_MS)
                }
            }
        pvrRefreshJob =
            viewModelScope.launch {
                while (isActive) {
                    // PVR ETA/speed comes from Sonarr/Radarr, not DownloadManager. Refreshing
                    // while this screen is visible keeps those values useful without tightening
                    // the app-wide background polling interval.
                    queueStatusRepository.refreshNow()
                    delay(PVR_REFRESH_INTERVAL_MS)
                }
            }
        viewModelScope.launch {
            downloader.getDeleteProgressFlow().collect { progress ->
                _state.update { it.copy(deleteProgress = progress) }
            }
        }
        viewModelScope.launch {
            queueStatusRepository.getQueueSnapshotFlow().collect { snapshot ->
                val groups = buildPvrQueueGroups(snapshot.entries)
                val liveKeys = groups.flatMap { g -> g.items.map { g.source to it.queueItemId } }.toSet()
                _state.update {
                    it.copy(
                        pvrQueueGroups = groups,
                        pvrErrors = snapshot.errors,
                        selectedPvrQueueIds = it.selectedPvrQueueIds.intersect(liveKeys),
                    )
                }
            }
        }
    }

    // Storage numbers don't change minute to minute, so this is a one-shot fetch on entering the
    // screen and on pull-to-refresh, not part of the polling loops above.
    //
    // These two fetches deliberately run as independent launches rather than sequentially, since
    // the PVR disk-space call is a network round trip (Sonarr/Radarr HTTP APIs, see
    // PvrDiskSpaceRepositoryImpl) while the device storage stats are a local StatFs call - no
    // reason to make the fast local one wait on the slow network one. This is also the root cause
    // of the previously-observed flaky on-device storage bar: with the old
    // `_state.value = _state.value.copy(...)` pattern, `_state.value` (the receiver of `.copy`) is
    // read/snapshotted *before* the suspending call on the same line, not after it resumes -
    // Kotlin evaluates a call's receiver before its arguments. So if this coroutine snapshots state
    // S0, then suspends on the network call, and the *other* launch below finishes first and writes
    // S1 = S0.copy(deviceStorage = ...), this coroutine resumes still holding stale S0 and does
    // `_state.value = S0.copy(diskSpace = ...)`, silently overwriting S1 and dropping the
    // deviceStorage update entirely. That race is timing-sensitive (depends on which of the two
    // calls happens to resolve first), which explains why it appeared/disappeared across
    // otherwise-identical runs. `_state.update { it.copy(...) }` fixes this: it always applies the
    // transform to the *current* value at the moment of the atomic update, never a pre-suspend
    // snapshot, so neither launch can clobber the other's write regardless of ordering.
    private fun refreshStorage() {
        viewModelScope.launch {
            val diskSpace = pvrDiskSpaceRepository.getDiskSpace()
            _state.update { it.copy(diskSpace = diskSpace) }
        }
        viewModelScope.launch {
            val deviceStorage = withContext(Dispatchers.IO) { downloader.getStorageStats() }
            _state.update { it.copy(deviceStorage = deviceStorage) }
        }
    }

    private suspend fun refreshDownloads() {
        if (_state.value.isEmpty) {
            _state.update { it.copy(isLoading = true, error = null) }
        }
        try {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return
            val userId = repository.getUserId()

            // toFindroidMovies/toFindroidEpisodes batch-fetch user data, sources, media streams
            // and trickplay info for the whole list up front (a handful of queries total) instead
            // of the per-row N+1 query pattern the singular toFindroidMovie/toFindroidEpisode do -
            // see their kdoc in FindroidMovie.kt/FindroidEpisode.kt for why that mattered here.
            val movies =
                withContext(Dispatchers.Default) {
                    database.getMoviesByServerId(serverId).toFindroidMovies(database, userId)
                }
            val episodes =
                withContext(Dispatchers.Default) {
                    database.getEpisodesByServerId(serverId).toFindroidEpisodes(database, userId)
                }
            val showGroups =
                withContext(Dispatchers.Default) {
                    episodes
                        .groupBy { it.seriesId }
                        .map { (seriesId, showEpisodes) ->
                            DownloadShowGroup(
                                seriesId = seriesId,
                                seriesName = showEpisodes.first().seriesName,
                                episodes =
                                    showEpisodes.sortedWith(
                                        compareBy({ it.parentIndexNumber }, { it.indexNumber })
                                    ),
                            )
                        }
                        .sortedBy { it.seriesName }
                }

            val allIds = (movies.map { it.id } + episodes.map { it.id }).toSet()
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    movies = movies,
                    showGroups = showGroups,
                    selectedIds = it.selectedIds.intersect(allIds),
                )
            }
            reconcileDownloadProgress(movies, episodes)
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, isRefreshing = false, error = e) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            queueStatusRepository.refreshNow()
            refreshDownloads()
        }
        refreshStorage()
    }

    private fun reconcileDownloadProgress(movies: List<FindroidMovie>, episodes: List<FindroidEpisode>) {
        val trackedItems: List<Pair<UUID, FindroidItem>> =
            movies.filter { it.isDownloading() }.map { it.id to it } +
                episodes.filter { it.isDownloading() }.map { it.id to it }
        val desiredIds = trackedItems.map { it.first }.toSet()

        (progressJobs.keys - desiredIds).forEach { id ->
            progressJobs.remove(id)?.cancel()
            downloadIdsByItem.remove(id)
            _state.update { it.copy(downloadProgress = it.downloadProgress - id) }
        }

        trackedItems.forEach { (id, item) ->
            if (progressJobs.containsKey(id)) return@forEach
            val downloadId =
                item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }?.downloadId
                    ?: return@forEach
            downloadIdsByItem[id] = downloadId
            progressJobs[id] =
                viewModelScope.launch {
                    downloader.getProgressFlow(downloadId).collect { progress ->
                        _state.update {
                            it.copy(downloadProgress = it.downloadProgress + (id to progress))
                        }
                    }
                }
        }
    }

    // Local-download selection and PVR-queue selection are mutually exclusive - both drive the
    // same top app bar (selection count, clear button, bulk-delete action), and a single bar can't
    // meaningfully represent two independent selections at once. Every toggle below clears the
    // other side's selection as soon as it goes from empty to non-empty.

    fun toggleSelection(id: UUID) {
        _state.update {
            val selectedIds = it.selectedIds
            val newSelectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            it.copy(
                selectedIds = newSelectedIds,
                selectedPvrQueueIds = if (newSelectedIds.isNotEmpty()) emptySet() else it.selectedPvrQueueIds,
            )
        }
    }

    fun toggleSelectAll(selectAll: Boolean) {
        _state.update { state ->
            val allIds =
                (state.movies.map { it.id } + state.showGroups.flatMap { it.episodes }.map { it.id })
                    .toSet()
            val newSelectedIds = if (selectAll) allIds else emptySet()
            state.copy(
                selectedIds = newSelectedIds,
                selectedPvrQueueIds = if (newSelectedIds.isNotEmpty()) emptySet() else state.selectedPvrQueueIds,
            )
        }
    }

    fun setGroupSelected(ids: Set<UUID>, selected: Boolean) {
        _state.update {
            val selectedIds = it.selectedIds
            val newSelectedIds = if (selected) selectedIds + ids else selectedIds - ids
            it.copy(
                selectedIds = newSelectedIds,
                selectedPvrQueueIds = if (newSelectedIds.isNotEmpty()) emptySet() else it.selectedPvrQueueIds,
            )
        }
    }

    fun togglePvrQueueSelection(source: PvrSource, queueItemId: Int) {
        _state.update {
            val key = source to queueItemId
            val selected = it.selectedPvrQueueIds
            val newSelected = if (key in selected) selected - key else selected + key
            it.copy(
                selectedPvrQueueIds = newSelected,
                selectedIds = if (newSelected.isNotEmpty()) emptySet() else it.selectedIds,
            )
        }
    }

    fun togglePvrQueueSelectAll(selectAll: Boolean) {
        _state.update { state ->
            val allKeys =
                state.pvrQueueGroups.flatMap { group -> group.items.map { group.source to it.queueItemId } }
                    .toSet()
            val newSelected = if (selectAll) allKeys else emptySet()
            state.copy(
                selectedPvrQueueIds = newSelected,
                selectedIds = if (newSelected.isNotEmpty()) emptySet() else state.selectedIds,
            )
        }
    }

    fun deleteSelected() {
        deleteItems(_state.value.selectedIds.toList())
    }

    fun deleteItem(id: UUID) {
        deleteItems(listOf(id))
    }

    fun deleteItems(ids: List<UUID>) {
        viewModelScope.launch {
            downloader.deleteItems(ids)
            _state.update { it.copy(selectedIds = it.selectedIds - ids.toSet()) }
            refreshDownloads()
        }
    }

    fun onDownloadAction(itemId: UUID, action: DownloadAction) {
        viewModelScope.launch {
            val downloadId = downloadIdsByItem[itemId] ?: return@launch
            when (action) {
                DownloadAction.Pause -> downloader.pauseDownload(downloadId)
                DownloadAction.Resume -> downloader.resumeDownload(downloadId)
                DownloadAction.Force -> downloader.forceDownload(downloadId)
                DownloadAction.Cancel -> {
                    downloader.cancelDownload(downloadId)
                    refreshDownloads()
                }
            }
        }
    }

    fun forceGroup(episodeIds: List<UUID>) {
        viewModelScope.launch {
            val downloadIds = episodeIds.mapNotNull { downloadIdsByItem[it] }
            if (downloadIds.isNotEmpty()) downloader.forceDownloadGroup(downloadIds)
        }
    }

    fun pauseAll() {
        viewModelScope.launch {
            downloadIdsByItem.values.toList().forEach { downloader.pauseDownload(it) }
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            downloadIdsByItem.values.toList().forEach { downloader.resumeDownload(it) }
        }
    }

    /**
     * Removes a Sonarr/Radarr queue entry (there is no API-side pause - that lives in the
     * download client). See [QueueStatusRepository.removeQueueItem] for the flag semantics.
     */
    fun removePvrQueueItem(
        item: PvrQueueUiItem,
        source: PvrSource,
        removeFromClient: Boolean,
        blocklist: Boolean,
    ) {
        viewModelScope.launch {
            queueStatusRepository
                .removeQueueItem(
                    source = source,
                    queueItemId = item.queueItemId,
                    removeFromClient = removeFromClient,
                    blocklist = blocklist,
                )
                .fold(
                    onSuccess = {
                        eventsChannel.send(DownloadsEvent.PvrQueueItemRemoved(item.title))
                    },
                    onFailure = { e ->
                        eventsChannel.send(DownloadsEvent.PvrQueueItemRemoveFailed(e.message))
                    },
                )
        }
    }

    /** Bulk version of [removePvrQueueItem] - e.g. "clear all pending downloads". */
    fun removeSelectedPvrQueueItems(removeFromClient: Boolean, blocklist: Boolean) {
        viewModelScope.launch {
            val selected = _state.value.selectedPvrQueueIds.toList()
            val failed =
                queueStatusRepository.removeQueueItems(selected, removeFromClient, blocklist)
            _state.update { it.copy(selectedPvrQueueIds = emptySet()) }
            eventsChannel.send(
                DownloadsEvent.PvrQueueItemsRemoved(
                    removed = selected.size - failed.size,
                    failed = failed.size,
                )
            )
        }
    }

    /**
     * Opens the "manage imports" sheet for a queue entry (see [ManualImportSheetState]) and kicks
     * off loading its candidate files. No-ops when the entry has no `downloadId` - shouldn't
     * happen in practice, but Sonarr/Radarr technically don't guarantee the field.
     */
    fun openManualImport(item: PvrQueueUiItem, source: PvrSource) {
        val downloadId = item.status.downloadId ?: return
        _state.update {
            it.copy(manualImport = ManualImportSheetState(source = source, downloadId = downloadId, title = item.title))
        }
        viewModelScope.launch {
            queueStatusRepository
                .getManualImportCandidates(source, downloadId)
                .fold(
                    onSuccess = { candidates ->
                        _state.update {
                            it.copy(
                                manualImport =
                                    it.manualImport?.copy(
                                        isLoading = false,
                                        candidates = candidates,
                                        selectedIds =
                                            candidates.filter { c -> c.canImport }.map { c -> c.id }.toSet(),
                                    )
                            )
                        }
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(manualImport = it.manualImport?.copy(isLoading = false, error = e.message))
                        }
                    },
                )
        }
    }

    fun closeManualImport() {
        _state.update { it.copy(manualImport = null) }
    }

    fun toggleManualImportSelection(candidateId: Int) {
        _state.update { state ->
            val current = state.manualImport ?: return@update state
            val selected = current.selectedIds
            val newSelected = if (candidateId in selected) selected - candidateId else selected + candidateId
            state.copy(manualImport = current.copy(selectedIds = newSelected))
        }
    }

    fun confirmManualImport() {
        val current = _state.value.manualImport ?: return
        if (current.selectedIds.isEmpty() || current.isImporting) return
        _state.update { it.copy(manualImport = it.manualImport?.copy(isImporting = true)) }
        viewModelScope.launch {
            queueStatusRepository
                .performManualImport(current.source, current.downloadId, current.selectedIds)
                .fold(
                    onSuccess = {
                        _state.update { it.copy(manualImport = null) }
                        eventsChannel.send(DownloadsEvent.ManualImportCompleted)
                    },
                    onFailure = { e ->
                        _state.update {
                            it.copy(manualImport = it.manualImport?.copy(isImporting = false, error = e.message))
                        }
                        eventsChannel.send(DownloadsEvent.ManualImportFailed(e.message))
                    },
                )
        }
    }

    fun clearAllDownloads(alsoRemoveRules: Boolean) {
        viewModelScope.launch {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val userId = repository.getUserId()

            val itemIds =
                withContext(Dispatchers.Default) {
                    database.getMoviesByServerId(serverId).map { it.id } +
                        database.getEpisodesByServerId(serverId).map { it.id }
                }
            downloader.deleteItems(itemIds)

            if (alsoRemoveRules) {
                autoDownloadRuleRepository.deleteAllRules(serverId, userId)
            }

            refreshDownloads()
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        pvrRefreshJob?.cancel()
        progressJobs.values.forEach { it.cancel() }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 3000L
        private const val PVR_REFRESH_INTERVAL_MS = 10_000L
    }
}

/**
 * Maps [QueueStatusRepository]'s queue-entry snapshot into the UI-facing [PvrQueueGroup] list.
 * Matched entries carry the live-library [FindroidItem][dev.jdtech.jellyfin.models.FindroidItem]
 * the repository resolved (poster + click-through); unmatched entries (e.g. a torrent added
 * manually on the PVR side for something not yet in Jellyfin) become title-only rows using the
 * PVR-side title the repository built.
 *
 * A free function (not a method) so it's directly unit-testable without a ViewModel/Hilt/Android
 * in the loop.
 */
internal fun buildPvrQueueGroups(entries: List<PvrQueueEntry>): List<PvrQueueGroup> =
    entries
        .groupBy { it.status.source }
        .map { (source, groupEntries) ->
            PvrQueueGroup(
                source = source,
                items =
                    groupEntries.map { entry ->
                        PvrQueueUiItem(
                            itemId = entry.item?.id,
                            title = entry.item.toQueueTitle(fallback = entry.title),
                            item = entry.item,
                            posterUrl = entry.posterUrl,
                            tmdbId = entry.tmdbId,
                            sonarrEpisodeId = entry.sonarrEpisodeId,
                            seasonNumber = entry.seasonNumber,
                            episodeNumber = entry.episodeNumber,
                            status = entry.status,
                            queueItemId = entry.queueItemId,
                        )
                    },
            )
        }

private fun FindroidItem?.toQueueTitle(fallback: String): String =
    when (this) {
        is FindroidEpisode -> "$seriesName - S${parentIndexNumber}E$indexNumber"
        is FindroidMovie -> name
        null -> fallback
        else -> name
    }
