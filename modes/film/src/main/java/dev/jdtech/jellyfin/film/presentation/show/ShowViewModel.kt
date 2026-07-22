package dev.jdtech.jellyfin.film.presentation.show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSizeEstimate
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.di.ApplicationScope
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.CalendarEntry
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.CalendarRepository
import dev.jdtech.jellyfin.repository.ExistingAutoDownloadScope
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.PendingDownloadRequestRepository
import dev.jdtech.jellyfin.repository.SeasonEpisodesRepository
import dev.jdtech.jellyfin.repository.SeerrRepository
import dev.jdtech.jellyfin.repository.toExistingScope
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.AutoDownloadRuleEvaluator
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.clearDownloads
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PersonKind
import timber.log.Timber

@HiltViewModel
class ShowViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val autoDownloadRuleRepository: AutoDownloadRuleRepository,
    private val appPreferences: AppPreferences,
    private val calendarRepository: CalendarRepository,
    private val seasonEpisodesRepository: SeasonEpisodesRepository,
    private val seerrRepository: SeerrRepository,
    private val pendingDownloadRequestRepository: PendingDownloadRequestRepository,
    @ApplicationScope private val externalScope: CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow(ShowState())
    val state = _state.asStateFlow()

    private val evaluator = AutoDownloadRuleEvaluator()

    lateinit var showId: UUID

    fun loadShow(showId: UUID) {
        this.showId = showId
        viewModelScope.launch {
            try {
                val show = repository.getShow(showId)
                val nextUp = getNextUp(showId)
                val nextAiring = if (nextUp == null) getNextAiring(showId) else null
                val seasons = repository.getSeasons(showId)
                val actors = getActors(show)
                val director = getDirector(show)
                val writers = getWriters(show)
                val autoDownloadEnabled = isAutoDownloadEnabled(showId)
                val existingScope = getExistingScope(showId)
                val downloadsSizeBytes = downloadsSizeBytes(showId)
                _state.emit(
                    _state.value.copy(
                        show = show,
                        nextUp = nextUp,
                        nextAiring = nextAiring,
                        seasons = seasons,
                        actors = actors,
                        director = director,
                        writers = writers,
                        autoDownloadEnabled = autoDownloadEnabled,
                        existingScope = existingScope,
                        hasDownloads = downloadsSizeBytes > 0,
                        downloadsSizeBytes = downloadsSizeBytes,
                        seriesTvdbId = show.tvdbId,
                        seriesTmdbId = show.tmdbId?.toIntOrNull(),
                    )
                )
                // Fired after the main state emit rather than blocking it - the real show/season
                // data is already on screen by the time this (possibly slow, Sonarr-dependent)
                // round trip resolves, same pattern as SeasonViewModel.loadUpcomingEpisodes.
                loadMissingSeasons(show.tvdbId, show.tmdbId?.toIntOrNull(), seasons)
                loadQueuedSeasons(showId)
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private suspend fun loadMissingSeasons(
        seriesTvdbId: String?,
        seriesTmdbId: Int?,
        knownSeasons: List<FindroidSeason>,
    ) {
        val missing =
            if (!appPreferences.getValue(appPreferences.sonarrEnabled) || seriesTvdbId == null) {
                emptyList()
            } else {
                try {
                    seasonEpisodesRepository.getMissingSeasons(
                        seriesTvdbId = seriesTvdbId,
                        knownSeasonNumbers = knownSeasons.map { it.indexNumber }.toSet(),
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load missing seasons for show $showId")
                    emptyList()
                }
            }
        _state.emit(_state.value.copy(missingSeasons = missing))
        if (missing.isEmpty() || seriesTmdbId == null) return
        if (!appPreferences.getValue(appPreferences.seerrEnabled)) return
        // Separate round trip after missingSeasons is already on screen - poster art is a nice-
        // to-have, not something worth delaying the placeholder cards themselves for.
        seerrRepository
            .getSeasonPosterUrls(seriesTmdbId, missing.map { it.seasonNumber })
            .onSuccess { posterUrls ->
                val withPosters = missing.map { it.copy(posterUrl = posterUrls[it.seasonNumber]) }
                _state.emit(_state.value.copy(missingSeasons = withPosters))
            }
            .onFailure { e -> Timber.w(e, "Failed to load missing-season posters for show $showId") }
    }

    private suspend fun loadQueuedSeasons(showId: UUID) {
        val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return
        val userId = repository.getUserId()
        val queued =
            pendingDownloadRequestRepository
                .getQueuedForSeries(serverId, userId, showId)
                .filter { it.episodeNumber == null }
                .map { it.seasonNumber }
                .toSet()
        _state.emit(_state.value.copy(queuedSeasonNumbers = queued))
    }

    private fun toggleSeasonQueued(seasonNumber: Int) {
        viewModelScope.launch {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val userId = repository.getUserId()
            val alreadyQueued = _state.value.queuedSeasonNumbers.contains(seasonNumber)
            if (alreadyQueued) {
                pendingDownloadRequestRepository.cancel(
                    serverId,
                    userId,
                    showId,
                    seasonNumber,
                    episodeNumber = null,
                )
            } else {
                pendingDownloadRequestRepository.queue(
                    serverId,
                    userId,
                    showId,
                    seasonNumber,
                    episodeNumber = null,
                    sonarrEpisodeId = null,
                )
            }
            loadQueuedSeasons(showId)
        }
    }

    private suspend fun isAutoDownloadEnabled(showId: UUID): Boolean {
        val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return false
        val userId = repository.getUserId()
        return autoDownloadRuleRepository.isShowRuleEnabled(serverId, userId, showId)
    }

    private suspend fun getExistingScope(showId: UUID): ExistingAutoDownloadScope {
        val serverId = appPreferences.getValue(appPreferences.currentServer)
            ?: return ExistingAutoDownloadScope()
        val userId = repository.getUserId()
        return autoDownloadRuleRepository.getRulesForSeries(serverId, userId, showId).toExistingScope()
    }

    private fun downloadWithScope(
        selection: DownloadSelection,
        alsoFollowNew: Boolean,
        onlyUnwatched: Boolean,
    ) {
        // Deliberately not viewModelScope: enqueuing a full show/season can take a while (one
        // network round trip per episode), and viewModelScope is cancelled the instant this
        // screen is popped off the back stack (e.g. the user taps another tab to check on
        // progress) - which silently truncated the batch partway through. externalScope survives
        // that navigation so every episode gets enqueued regardless of what the user does next.
        externalScope.launch {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val userId = repository.getUserId()

            // Only the explicitly-picked seasons are downloaded immediately - "auto-download
            // future seasons" is a no-op today by definition, it only matters once persisted.
            for (seasonId in selection.seasonIds) {
                val transientRule =
                    AutoDownloadRuleDto(
                        serverId = serverId,
                        userId = userId,
                        seriesId = showId,
                        seasonId = seasonId,
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
                    seriesId = showId,
                    seasonIds = if (alsoFollowNew) selection.seasonIds else emptySet(),
                    alsoFutureSeasons = selection.alsoFutureSeasons,
                    onlyNewEpisodes = false,
                    onlyUnwatched = onlyUnwatched,
                )
            }
            loadShow(showId)
        }
    }

    /** Count and total primary-source size of [seasonId]'s episodes that would actually be
     * downloaded right now - excludes episodes already downloaded locally, and (if
     * [onlyUnwatched]) already-watched ones, matching the scope the "only unwatched" toggle
     * would apply to the real download. */
    suspend fun getUndownloadedEpisodeSize(
        seasonId: UUID,
        onlyUnwatched: Boolean,
    ): DownloadSizeEstimate {
        val episodes =
            try {
                repository.getEpisodes(
                    seriesId = showId,
                    seasonId = seasonId,
                    fields = listOf(ItemFields.MEDIA_SOURCES),
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch episode sizes for season $seasonId")
                return DownloadSizeEstimate()
            }
        return withContext(Dispatchers.IO) {
            val pending =
                episodes
                    .filter { !onlyUnwatched || !it.played }
                    .filter { database.getSources(it.id).isEmpty() }
            DownloadSizeEstimate(
                sizeBytes = pending.sumOf { it.sources.firstOrNull()?.size ?: 0 },
                itemCount = pending.size,
            )
        }
    }

    private suspend fun downloadsSizeBytes(showId: UUID): Long =
        withContext(Dispatchers.IO) {
            database.getEpisodesByShowId(showId).sumOf { episode ->
                database
                    .getSources(episode.id)
                    .filter { it.type == FindroidSourceType.LOCAL }
                    .sumOf { File(it.path).length() }
            }
        }

    private fun deleteShowDownloads(alsoRemoveRules: Boolean) {
        viewModelScope.launch {
            val userId = repository.getUserId()
            val episodes =
                withContext(Dispatchers.IO) {
                    database.getEpisodesByShowId(showId).map { it.toFindroidEpisode(database, userId) }
                }
            clearDownloads(episodes, database, downloader)

            if (alsoRemoveRules) {
                appPreferences.getValue(appPreferences.currentServer)?.let { serverId ->
                    autoDownloadRuleRepository.deleteRulesForShow(serverId, userId, showId)
                }
            }

            loadShow(showId)
        }
    }

    private suspend fun getNextUp(showId: UUID): FindroidEpisode? {
        val nextUpItems = repository.getNextUp(showId)
        return nextUpItems.getOrNull(0)
    }

    private suspend fun getNextAiring(showId: UUID): CalendarEntry? {
        return try {
            calendarRepository
                .getUpcoming()
                .entries
                .filter { it.itemId == showId }
                .minByOrNull { it.date }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getActors(item: FindroidShow): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.ACTOR }
        }
    }

    private suspend fun getDirector(item: FindroidShow): FindroidItemPerson? {
        return withContext(Dispatchers.Default) {
            item.people.firstOrNull { it.type == PersonKind.DIRECTOR }
        }
    }

    private suspend fun getWriters(item: FindroidShow): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.WRITER }
        }
    }

    fun onAction(action: ShowAction) {
        when (action) {
            is ShowAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.DownloadWithScope ->
                downloadWithScope(action.selection, action.alsoFollowNew, action.onlyUnwatched)
            is ShowAction.DeleteShowDownloads -> deleteShowDownloads(action.alsoRemoveRules)
            is ShowAction.ToggleSeasonQueued -> toggleSeasonQueued(action.seasonNumber)
            else -> Unit
        }
    }
}
