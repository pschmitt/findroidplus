package dev.jdtech.jellyfin.film.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.clearDownloads
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
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadsState())
    val state = _state.asStateFlow()

    // itemId (movie or episode id) -> downloadId for every item currently being tracked by
    // progressJobs, so pause/resume/cancel can turn an item id from the UI into the downloadId
    // the Downloader API needs.
    private val downloadIdsByItem = mutableMapOf<UUID, Long>()
    private val progressJobs = mutableMapOf<UUID, Job>()
    private var refreshJob: Job? = null

    fun startObserving() {
        if (refreshJob != null) return
        refreshJob =
            viewModelScope.launch {
                while (isActive) {
                    refresh()
                    delay(REFRESH_INTERVAL_MS)
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
        viewModelScope.launch {
            val selectedIds = _state.value.selectedIds
            val items: List<FindroidItem> =
                _state.value.movies.filter { it.id in selectedIds } +
                    _state.value.showGroups.flatMap { it.episodes }.filter { it.id in selectedIds }
            clearDownloads(items, database, downloader)
            refresh()
        }
    }

    fun deleteItem(id: UUID) {
        viewModelScope.launch {
            val item: FindroidItem =
                _state.value.movies.find { it.id == id }
                    ?: _state.value.showGroups.flatMap { it.episodes }.find { it.id == id }
                    ?: return@launch
            clearDownloads(listOf(item), database, downloader)
            _state.value = _state.value.copy(selectedIds = _state.value.selectedIds - id)
            refresh()
        }
    }

    fun onDownloadAction(itemId: UUID, action: DownloadAction) {
        viewModelScope.launch {
            val downloadId = downloadIdsByItem[itemId] ?: return@launch
            when (action) {
                DownloadAction.Pause -> downloader.pauseDownload(downloadId)
                DownloadAction.Resume -> downloader.resumeDownload(downloadId)
                DownloadAction.Cancel -> {
                    downloader.cancelDownload(downloadId)
                    refresh()
                }
            }
        }
    }

    fun clearAllDownloads(alsoRemoveRules: Boolean) {
        viewModelScope.launch {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val userId = repository.getUserId()

            val items: List<FindroidItem> =
                withContext(Dispatchers.Default) {
                    database.getMoviesByServerId(serverId).map { it.toFindroidMovie(database, userId) } +
                        database.getEpisodesByServerId(serverId).map {
                            it.toFindroidEpisode(database, userId)
                        }
                }
            clearDownloads(items, database, downloader)

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
