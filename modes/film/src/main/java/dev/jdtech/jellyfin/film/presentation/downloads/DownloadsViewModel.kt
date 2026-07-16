package dev.jdtech.jellyfin.film.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadsState())
    val state = _state.asStateFlow()

    // itemId (movie or episode id) -> downloadId for every item currently being tracked by
    // progressJobs, so pause/resume/cancel can turn an item id from the UI into the downloadId
    // the Downloader API needs.
    private val downloadIdsByItem = mutableMapOf<UUID, Long>()
    private val progressJobs = mutableMapOf<UUID, Job>()
    private var refreshJob: Job? = null

    // Latest snapshot from QueueStatusRepository, kept around so a library refresh (which can
    // resolve/lose local item matches) and a queue-status update (which arrives on its own poll
    // cadence) both recompute pvrQueueGroups off the same up-to-date inputs.
    private var latestQueueStatus: Map<UUID, QueueStatus> = emptyMap()

    fun startObserving() {
        if (refreshJob != null) return
        refreshJob =
            viewModelScope.launch {
                while (isActive) {
                    refresh()
                    delay(REFRESH_INTERVAL_MS)
                }
            }
        viewModelScope.launch {
            downloader.getDeleteProgressFlow().collect { progress ->
                _state.value = _state.value.copy(deleteProgress = progress)
            }
        }
        viewModelScope.launch {
            queueStatusRepository.getQueueStatusFlow().collect { queueStatus ->
                latestQueueStatus = queueStatus
                _state.value =
                    _state.value.copy(
                        pvrQueueGroups =
                            buildPvrQueueGroups(
                                queueStatus,
                                _state.value.movies,
                                _state.value.showGroups,
                            )
                    )
            }
        }
    }

    private suspend fun refresh() {
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
                    movies = movies,
                    showGroups = showGroups,
                    selectedIds = _state.value.selectedIds.intersect(allIds),
                    pvrQueueGroups = buildPvrQueueGroups(latestQueueStatus, movies, showGroups),
                )
            reconcileDownloadProgress(movies, episodes)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = e)
        }
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
            refresh()
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
                    refresh()
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

            refresh()
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        progressJobs.values.forEach { it.cancel() }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 3000L
    }
}

/**
 * Maps [QueueStatusRepository]'s raw `itemId -> QueueStatus` snapshot into the UI-facing
 * [PvrQueueGroup] list, resolving each item id against the movies/episodes already loaded from
 * Room. `QueueStatusRepository` only ever keys its map by an id it already matched to a real
 * local Jellyfin item (see `matchSonarr`/`matchRadarr`), so [PvrQueueUiItem.itemId] should always
 * resolve here in practice - the null-itemId/title-only branch exists purely as a defensive
 * fallback for the (theoretical) case where the local item disappeared between the repository's
 * match and this lookup, e.g. a delete racing a poll.
 *
 * A free function (not a method) so it's directly unit-testable without a ViewModel/Hilt/Android
 * in the loop.
 */
internal fun buildPvrQueueGroups(
    queueStatus: Map<UUID, QueueStatus>,
    movies: List<FindroidMovie>,
    showGroups: List<DownloadShowGroup>,
): List<PvrQueueGroup> {
    if (queueStatus.isEmpty()) return emptyList()

    val itemsById: Map<UUID, FindroidItem> =
        (movies.associateBy { it.id }) + (showGroups.flatMap { it.episodes }.associateBy { it.id })

    return queueStatus.entries
        .groupBy { (_, status) -> status.source }
        .map { (source, entries) ->
            PvrQueueGroup(
                source = source,
                items =
                    entries.map { (id, status) ->
                        val item = itemsById[id]
                        PvrQueueUiItem(
                            itemId = item?.id,
                            title = item.toQueueTitle(),
                            item = item,
                            status = status,
                        )
                    },
            )
        }
}

private fun FindroidItem?.toQueueTitle(): String =
    when (this) {
        is FindroidEpisode -> "$seriesName - S${parentIndexNumber}E$indexNumber"
        is FindroidMovie -> name
        null -> "Unknown item"
        else -> name
    }
