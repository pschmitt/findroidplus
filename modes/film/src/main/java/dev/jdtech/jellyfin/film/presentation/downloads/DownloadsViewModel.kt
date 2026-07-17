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
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
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
                _state.value = _state.value.copy(deleteProgress = progress)
            }
        }
        viewModelScope.launch {
            queueStatusRepository.getQueueSnapshotFlow().collect { snapshot ->
                _state.value =
                    _state.value.copy(
                        pvrQueueGroups = buildPvrQueueGroups(snapshot.entries),
                        pvrErrors = snapshot.errors,
                    )
            }
        }
        refreshStorage()
    }

    // Storage numbers don't change minute to minute, so this is a one-shot fetch on entering the
    // screen and on pull-to-refresh, not part of the polling loops above.
    private fun refreshStorage() {
        viewModelScope.launch {
            _state.value = _state.value.copy(diskSpace = pvrDiskSpaceRepository.getDiskSpace())
        }
        viewModelScope.launch {
            val deviceStorage = withContext(Dispatchers.IO) { downloader.getStorageStats() }
            _state.value = _state.value.copy(deviceStorage = deviceStorage)
        }
    }

    private suspend fun refreshDownloads() {
        if (_state.value.isEmpty) {
            _state.value = _state.value.copy(isLoading = true, error = null)
        }
        try {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return
            val userId = repository.getUserId()

            val movies =
                withContext(Dispatchers.Default) {
                    database.getMoviesByServerId(serverId).map { it.toFindroidMovie(database, userId) }
                }
            val episodes =
                withContext(Dispatchers.Default) {
                    database.getEpisodesByServerId(serverId).map { it.toFindroidEpisode(database, userId) }
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
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    movies = movies,
                    showGroups = showGroups,
                    selectedIds = _state.value.selectedIds.intersect(allIds),
                )
            reconcileDownloadProgress(movies, episodes)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, isRefreshing = false, error = e)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
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
            _state.value = _state.value.copy(downloadProgress = _state.value.downloadProgress - id)
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
                        _state.value =
                            _state.value.copy(
                                downloadProgress = _state.value.downloadProgress + (id to progress)
                            )
                    }
                }
        }
    }

    fun toggleSelection(id: UUID) {
        val selectedIds = _state.value.selectedIds
        _state.value =
            _state.value.copy(
                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            )
    }

    fun toggleSelectAll(selectAll: Boolean) {
        val allIds =
            (_state.value.movies.map { it.id } + _state.value.showGroups.flatMap { it.episodes }.map { it.id })
                .toSet()
        _state.value = _state.value.copy(selectedIds = if (selectAll) allIds else emptySet())
    }

    fun setGroupSelected(ids: Set<UUID>, selected: Boolean) {
        val selectedIds = _state.value.selectedIds
        _state.value =
            _state.value.copy(
                selectedIds = if (selected) selectedIds + ids else selectedIds - ids
            )
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
            _state.value = _state.value.copy(selectedIds = _state.value.selectedIds - ids.toSet())
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
