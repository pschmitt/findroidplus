package dev.jdtech.jellyfin.film.presentation.episode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.core.presentation.search.ReleasePickerState
import dev.jdtech.jellyfin.core.presentation.search.SearchEvent
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.film.domain.VideoMetadataParser
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.ExistingAutoDownloadScope
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.SonarrSearchRepository
import dev.jdtech.jellyfin.repository.toExistingScope
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.AutoDownloadRuleEvaluator
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind

@HiltViewModel
class EpisodeViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val videoMetadataParser: VideoMetadataParser,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val autoDownloadRuleRepository: AutoDownloadRuleRepository,
    private val sonarrSearchRepository: SonarrSearchRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EpisodeState())
    val state = _state.asStateFlow()

    private val searchEventsChannel = Channel<SearchEvent>()
    val searchEvents = searchEventsChannel.receiveAsFlow()

    private val evaluator = AutoDownloadRuleEvaluator()

    lateinit var episodeId: UUID

    fun loadEpisode(episodeId: UUID) {
        this.episodeId = episodeId
        viewModelScope.launch {
            try {
                val episode = repository.getEpisode(episodeId)
                val videoMetadata = videoMetadataParser.parse(episode.sources.first())
                val actors = getActors(episode)
                val dateFormat = appPreferences.getValue(appPreferences.dateFormat)
                val existingScope = getExistingScope(episode.seriesId)
                val seriesTvdbId = repository.getShow(episode.seriesId).tvdbId
                _state.emit(
                    _state.value.copy(
                        episode = episode,
                        videoMetadata = videoMetadata,
                        actors = actors,
                        dateFormat = dateFormat,
                        existingScope = existingScope,
                        seriesTvdbId = seriesTvdbId,
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private suspend fun resolveTargetEpisodeId(): Int? {
        val episode = _state.value.episode ?: return null
        val seriesTvdbId = _state.value.seriesTvdbId ?: return null
        return sonarrSearchRepository.resolveEpisodeId(
            seriesTvdbId,
            episode.parentIndexNumber,
            episode.indexNumber,
        )
    }

    private fun searchEpisodeAutomatic() {
        viewModelScope.launch {
            val episodeId = resolveTargetEpisodeId()
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

    private fun openReleasePicker() {
        viewModelScope.launch {
            _state.value = _state.value.copy(releasePicker = ReleasePickerState())
            val episodeId = resolveTargetEpisodeId()
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

    private suspend fun getActors(item: FindroidEpisode): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.ACTOR }
        }
    }

    suspend fun getSeasons(): List<FindroidSeason> {
        val seriesId = _state.value.episode?.seriesId ?: return emptyList()
        return repository.getSeasons(seriesId)
    }

    private suspend fun getExistingScope(seriesId: UUID): ExistingAutoDownloadScope {
        val serverId = appPreferences.getValue(appPreferences.currentServer)
            ?: return ExistingAutoDownloadScope()
        val userId = repository.getUserId()
        return autoDownloadRuleRepository.getRulesForSeries(serverId, userId, seriesId).toExistingScope()
    }

    private fun downloadWithScope(
        selection: DownloadSelection,
        alsoFollowNew: Boolean,
        onlyUnwatched: Boolean,
    ) {
        val episode = _state.value.episode ?: return
        viewModelScope.launch {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val userId = repository.getUserId()

            for (targetSeasonId in selection.seasonIds) {
                val transientRule =
                    AutoDownloadRuleDto(
                        serverId = serverId,
                        userId = userId,
                        seriesId = episode.seriesId,
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
                    seriesId = episode.seriesId,
                    seasonIds = if (alsoFollowNew) selection.seasonIds else emptySet(),
                    alsoFutureSeasons = selection.alsoFutureSeasons,
                    onlyNewEpisodes = false,
                    onlyUnwatched = onlyUnwatched,
                )
            }
        }
    }

    fun onAction(action: EpisodeAction) {
        when (action) {
            is EpisodeAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(episodeId)
                    loadEpisode(episodeId)
                }
            }
            is EpisodeAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(episodeId)
                    loadEpisode(episodeId)
                }
            }
            is EpisodeAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(episodeId)
                    loadEpisode(episodeId)
                }
            }
            is EpisodeAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(episodeId)
                    loadEpisode(episodeId)
                }
            }
            is EpisodeAction.DownloadWithScope ->
                downloadWithScope(action.selection, action.alsoFollowNew, action.onlyUnwatched)
            is EpisodeAction.SearchEpisodeAutomatic -> searchEpisodeAutomatic()
            is EpisodeAction.OpenReleasePicker -> openReleasePicker()
            is EpisodeAction.GrabRelease -> grabRelease(action.release)
            is EpisodeAction.DismissReleasePicker ->
                _state.value = _state.value.copy(releasePicker = null)
            else -> Unit
        }
    }
}
