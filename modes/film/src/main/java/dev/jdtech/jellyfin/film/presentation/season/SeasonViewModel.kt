package dev.jdtech.jellyfin.film.presentation.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.core.presentation.search.ReleasePickerState
import dev.jdtech.jellyfin.core.presentation.search.SearchEvent
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.UpcomingEpisode
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.pvr.PvrConfiguration
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.ExistingAutoDownloadScope
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import dev.jdtech.jellyfin.repository.SeasonEpisodesRepository
import dev.jdtech.jellyfin.repository.SonarrSearchRepository
import dev.jdtech.jellyfin.repository.toExistingScope
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.AutoDownloadRuleEvaluator
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.clearDownloads
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

@HiltViewModel
class SeasonViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val autoDownloadRuleRepository: AutoDownloadRuleRepository,
    private val appPreferences: AppPreferences,
    private val queueStatusRepository: QueueStatusRepository,
    private val seasonEpisodesRepository: SeasonEpisodesRepository,
    private val sonarrSearchRepository: SonarrSearchRepository,
    private val pvrConfiguration: PvrConfiguration,
) : ViewModel() {
    private val _state = MutableStateFlow(SeasonState())
    val state = _state.asStateFlow()

    private val searchEventsChannel = Channel<SearchEvent>()
    val searchEvents = searchEventsChannel.receiveAsFlow()

    private val evaluator = AutoDownloadRuleEvaluator()

    private val downloadIdsByEpisode = mutableMapOf<UUID, Long>()
    private val progressJobs = mutableMapOf<UUID, Job>()
    private var queueStatusJob: Job? = null

    lateinit var seasonId: UUID
    private var seriesId: UUID? = null

    fun loadSeason(seasonId: UUID) {
        this.seasonId = seasonId
        viewModelScope.launch {
            try {
                val season = repository.getSeason(seasonId)
                seriesId = season.seriesId
                val episodes =
                    repository.getEpisodes(
                        seriesId = season.seriesId,
                        seasonId = seasonId,
                        fields = listOf(ItemFields.OVERVIEW),
                    )
                val autoDownloadEnabled = isAutoDownloadEnabled(season.seriesId, seasonId)
                val existingScope = getExistingScope(season.seriesId)
                val downloadsSizeBytes = downloadsSizeBytes(seasonId)
                val series = repository.getShow(season.seriesId)
                val seriesTvdbId = series.tvdbId
                _state.emit(
                    _state.value.copy(
                        season = season,
                        episodes = episodes,
                        autoDownloadEnabled = autoDownloadEnabled,
                        existingScope = existingScope,
                        hasDownloads = downloadsSizeBytes > 0,
                        downloadsSizeBytes = downloadsSizeBytes,
                        seriesTvdbId = seriesTvdbId,
                        seriesTmdbId = series.tmdbId?.toIntOrNull(),
                        sonarrConfigured = pvrConfiguration.isSonarrConfigured(),
                    )
                )
                reconcileDownloadProgress(episodes)
                observeQueueStatus(episodes)
                loadUpcomingEpisodes(seriesTvdbId, season.indexNumber, episodes)
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private suspend fun loadUpcomingEpisodes(
        seriesTvdbId: String?,
        seasonNumber: Int,
        knownEpisodes: List<FindroidEpisode>,
    ) {
        val upcoming =
            if (!appPreferences.getValue(appPreferences.sonarrEnabled) || seriesTvdbId == null) {
                emptyList()
            } else {
                try {
                    seasonEpisodesRepository.getUpcomingEpisodes(
                        seriesTvdbId = seriesTvdbId,
                        seasonNumber = seasonNumber,
                        knownEpisodeNumbers = knownEpisodes.map { it.indexNumber }.toSet(),
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load upcoming episodes for season $seasonNumber")
                    emptyList()
                }
            }
        _state.emit(_state.value.copy(upcomingEpisodes = upcoming))
    }

    /** Resolves the Sonarr episode id to act on - already known for upcoming-episode rows
     * ([knownEpisodeId]), otherwise resolved from [SeasonState.seriesTvdbId]. */
    private suspend fun resolveTargetEpisodeId(episodeNumber: Int, knownEpisodeId: Int?): Int? {
        if (knownEpisodeId != null) return knownEpisodeId
        val seriesTvdbId = _state.value.seriesTvdbId ?: return null
        val seasonNumber = _state.value.season?.indexNumber ?: return null
        return sonarrSearchRepository.resolveEpisodeId(seriesTvdbId, seasonNumber, episodeNumber)
    }

    private fun searchEpisodeAutomatic(episodeNumber: Int, knownEpisodeId: Int?) {
        viewModelScope.launch {
            val episodeId = resolveTargetEpisodeId(episodeNumber, knownEpisodeId)
            val event =
                if (episodeId == null) {
                    SearchEvent.Failed("Could not find this episode in Sonarr")
                } else {
                    sonarrSearchRepository
                        .searchEpisode(episodeId)
                        .fold({ SearchEvent.SearchTriggered }, { SearchEvent.Failed(it.message) })
                }
            searchEventsChannel.send(event)
        }
    }

    private fun openReleasePicker(episodeNumber: Int, knownEpisodeId: Int?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(releasePicker = ReleasePickerState())
            val episodeId = resolveTargetEpisodeId(episodeNumber, knownEpisodeId)
            if (episodeId == null) {
                _state.value = _state.value.copy(releasePicker = null)
                searchEventsChannel.send(SearchEvent.Failed("Could not find this episode in Sonarr"))
                return@launch
            }
            val result = sonarrSearchRepository.getReleases(episodeId)
            _state.value =
                _state.value.copy(
                    releasePicker = result.getOrNull()?.let { ReleasePickerState(isLoading = false, releases = it) }
                )
            result.onFailure { searchEventsChannel.send(SearchEvent.Failed(it.message)) }
        }
    }

    private fun grabRelease(release: PvrRelease) {
        viewModelScope.launch {
            val result = sonarrSearchRepository.grabRelease(release)
            _state.value = _state.value.copy(releasePicker = null)
            searchEventsChannel.send(
                result.fold({ SearchEvent.ReleaseGrabbed }, { SearchEvent.Failed(it.message) })
            )
        }
    }

    private fun observeQueueStatus(episodes: List<FindroidEpisode>) {
        val episodeIds = episodes.map { it.id }.toSet()
        queueStatusJob?.cancel()
        queueStatusJob =
            viewModelScope.launch {
                queueStatusRepository.getQueueStatusFlow().collect { queueStatusByItemId ->
                    _state.value =
                        _state.value.copy(
                            queueStatus = queueStatusByItemId.filterKeys { it in episodeIds }
                        )
                }
            }
    }

    private fun reconcileDownloadProgress(episodes: List<FindroidEpisode>) {
        val trackedEpisodes = episodes.filter { it.isDownloading() }
        val desiredIds = trackedEpisodes.map { it.id }.toSet()

        (progressJobs.keys - desiredIds).forEach { id ->
            progressJobs.remove(id)?.cancel()
            downloadIdsByEpisode.remove(id)
            _state.value = _state.value.copy(downloadProgress = _state.value.downloadProgress - id)
        }

        trackedEpisodes.forEach { episode ->
            if (progressJobs.containsKey(episode.id)) return@forEach
            val downloadId =
                episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }?.downloadId
                    ?: return@forEach
            downloadIdsByEpisode[episode.id] = downloadId
            progressJobs[episode.id] =
                viewModelScope.launch {
                    downloader.getProgressFlow(downloadId).collect { progress ->
                        _state.value =
                            _state.value.copy(
                                downloadProgress =
                                    _state.value.downloadProgress + (episode.id to progress)
                            )
                    }
                }
        }
    }

    private suspend fun isAutoDownloadEnabled(seriesId: UUID, seasonId: UUID): Boolean {
        val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return false
        val userId = repository.getUserId()
        return autoDownloadRuleRepository.isSeasonRuleEnabled(serverId, userId, seriesId, seasonId)
    }

    private suspend fun getExistingScope(seriesId: UUID): ExistingAutoDownloadScope {
        val serverId = appPreferences.getValue(appPreferences.currentServer)
            ?: return ExistingAutoDownloadScope()
        val userId = repository.getUserId()
        return autoDownloadRuleRepository.getRulesForSeries(serverId, userId, seriesId).toExistingScope()
    }

    suspend fun getSeasons(): List<FindroidSeason> {
        val seriesId = seriesId ?: return emptyList()
        return repository.getSeasons(seriesId)
    }

    private fun downloadWithScope(
        selection: DownloadSelection,
        alsoFollowNew: Boolean,
        onlyUnwatched: Boolean,
    ) {
        val seriesId = seriesId ?: return
        viewModelScope.launch {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val userId = repository.getUserId()

            for (targetSeasonId in selection.seasonIds) {
                val transientRule =
                    AutoDownloadRuleDto(
                        serverId = serverId,
                        userId = userId,
                        seriesId = seriesId,
                        seasonId = targetSeasonId,
                        enabled = true,
                        createdAt = System.currentTimeMillis(),
                        onlyNewEpisodes = false,
                    )
                evaluator.evaluate(transientRule, database, repository, downloader, onlyUnwatched)
            }

            if (alsoFollowNew || selection.alsoFutureSeasons) {
                autoDownloadRuleRepository.reconcileRules(
                    serverId = serverId,
                    userId = userId,
                    seriesId = seriesId,
                    seasonIds = if (alsoFollowNew) selection.seasonIds else emptySet(),
                    alsoFutureSeasons = selection.alsoFutureSeasons,
                    onlyNewEpisodes = false,
                    onlyUnwatched = onlyUnwatched,
                )
            }
            loadSeason(seasonId)
        }
    }

    private suspend fun downloadsSizeBytes(seasonId: UUID): Long =
        withContext(Dispatchers.IO) {
            database.getEpisodesBySeasonId(seasonId).sumOf { episode ->
                database
                    .getSources(episode.id)
                    .filter { it.type == FindroidSourceType.LOCAL }
                    .sumOf { File(it.path).length() }
            }
        }

    private fun deleteSeasonDownloads(alsoRemoveRules: Boolean) {
        val seriesId = seriesId ?: return
        viewModelScope.launch {
            val userId = repository.getUserId()
            val episodes =
                withContext(Dispatchers.IO) {
                    database.getEpisodesBySeasonId(seasonId).map {
                        it.toFindroidEpisode(database, userId)
                    }
                }
            clearDownloads(episodes, database, downloader)

            if (alsoRemoveRules) {
                appPreferences.getValue(appPreferences.currentServer)?.let { serverId ->
                    autoDownloadRuleRepository.deleteSeasonRule(serverId, userId, seriesId, seasonId)
                }
            }

            loadSeason(seasonId)
        }
    }

    fun onAction(action: SeasonAction) {
        when (action) {
            is SeasonAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(seasonId)
                    loadSeason(seasonId)
                }
            }
            is SeasonAction.DownloadWithScope ->
                downloadWithScope(action.selection, action.alsoFollowNew, action.onlyUnwatched)
            is SeasonAction.DeleteSeasonDownloads -> deleteSeasonDownloads(action.alsoRemoveRules)
            is SeasonAction.SearchEpisodeAutomatic ->
                searchEpisodeAutomatic(action.episodeNumber, action.knownEpisodeId)
            is SeasonAction.OpenReleasePicker ->
                openReleasePicker(action.episodeNumber, action.knownEpisodeId)
            is SeasonAction.GrabRelease -> grabRelease(action.release)
            is SeasonAction.DismissReleasePicker ->
                _state.value = _state.value.copy(releasePicker = null)
            else -> Unit
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJobs.values.forEach { it.cancel() }
        queueStatusJob?.cancel()
    }
}
