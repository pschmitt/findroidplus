package dev.jdtech.jellyfin.film.presentation.autodownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.toExistingScope
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.clearDownloads
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class AutoDownloadRulesViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val ruleRepository: AutoDownloadRuleRepository,
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
) : ViewModel() {
    private val _state = MutableStateFlow(AutoDownloadRulesState())
    val state = _state.asStateFlow()

    suspend fun getSeasons(seriesId: UUID): List<FindroidSeason> = repository.getSeasons(seriesId)

    fun loadRules() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                val serverId =
                    appPreferences.getValue(appPreferences.currentServer)
                        ?: run {
                            _state.emit(_state.value.copy(isLoading = false))
                            return@launch
                        }
                val userId = repository.getUserId()
                val rules = ruleRepository.getRules(serverId, userId)
                val uiModels =
                    rules
                        .groupBy { it.seriesId }
                        .mapNotNull { (seriesId, rulesForShow) ->
                            try {
                                toUiModel(seriesId, rulesForShow)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to resolve auto-download rules for $seriesId")
                                null
                            }
                        }
                _state.emit(_state.value.copy(isLoading = false, shows = uiModels))
            } catch (e: Exception) {
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }

    private suspend fun toUiModel(
        seriesId: UUID,
        rules: List<AutoDownloadRuleDto>,
    ): AutoDownloadShowRuleUiModel {
        val show = repository.getShow(seriesId)
        val scope = rules.toExistingScope()
        // Season-specific rules are the representative ones for onlyNewEpisodes/onlyUnwatched -
        // the future-seasons row is always onlyNewEpisodes=true by definition and isn't a useful
        // representative of the show's actual backfill preference.
        val primary = rules.find { it.seasonId != null } ?: rules.first()
        val scopeLabel =
            when {
                scope.seasonIds.isEmpty() ->
                    UiText.StringResource(CoreR.string.auto_download_rule_future_seasons)
                scope.seasonIds.size > 1 ->
                    UiText.StringResource(
                        CoreR.string.auto_download_rule_multiple_seasons,
                        scope.seasonIds.size,
                    )
                else -> {
                    val season = repository.getSeason(scope.seasonIds.first())
                    UiText.StringResource(CoreR.string.auto_download_rule_season, season.indexNumber)
                }
            }
        val (downloadedSizeBytes, downloadedSamplePath) = downloadedSize(seriesId, scope.seasonIds)
        return AutoDownloadShowRuleUiModel(
            seriesId = seriesId,
            ruleIds = rules.map { it.id },
            showName = show.name,
            enabled = rules.any { it.enabled },
            seasonIds = scope.seasonIds,
            alsoFutureSeasons = scope.alsoFutureSeasons,
            scopeLabel = scopeLabel,
            onlyNewEpisodes = primary.onlyNewEpisodes,
            onlyUnwatched = primary.onlyUnwatched,
            downloadedSizeBytes = downloadedSizeBytes,
            downloadedSamplePath = downloadedSamplePath,
        )
    }

    // Empty seasonIds means a future-seasons-only rule, which by definition tracks the whole show
    // rather than a fixed set of seasons - so that's the only case where we fall back to the
    // show's full download footprint instead of scoping to specific seasons. The sample path is
    // just one of the local sources contributing to the total, so LocalStorageIndicator can show
    // the right internal/removable icon - which volume any individual file happens to sit on.
    private suspend fun downloadedSize(seriesId: UUID, seasonIds: Set<UUID>): Pair<Long, String?> =
        withContext(Dispatchers.IO) {
            val episodes = database.getEpisodesByShowId(seriesId)
            val scoped =
                if (seasonIds.isEmpty()) episodes else episodes.filter { it.seasonId in seasonIds }
            val localSources =
                scoped.flatMap { episode ->
                    database.getSources(episode.id).filter { it.type == FindroidSourceType.LOCAL }
                }
            localSources.sumOf { File(it.path).length() } to localSources.firstOrNull()?.path
        }

    private fun toggleShowRule(seriesId: UUID, enabled: Boolean) {
        val ruleIds = _state.value.shows.find { it.seriesId == seriesId }?.ruleIds ?: return
        viewModelScope.launch {
            ruleIds.forEach { ruleRepository.setRuleEnabled(it, enabled) }
            loadRules()
        }
    }

    private fun updateShowRule(
        seriesId: UUID,
        seasonIds: Set<UUID>,
        alsoFutureSeasons: Boolean,
        onlyNewEpisodes: Boolean,
        onlyUnwatched: Boolean,
    ) {
        viewModelScope.launch {
            val serverId = appPreferences.getValue(appPreferences.currentServer) ?: return@launch
            val userId = repository.getUserId()
            ruleRepository.reconcileRules(
                serverId = serverId,
                userId = userId,
                seriesId = seriesId,
                seasonIds = seasonIds,
                alsoFutureSeasons = alsoFutureSeasons,
                onlyNewEpisodes = onlyNewEpisodes,
                onlyUnwatched = onlyUnwatched,
            )
            loadRules()
        }
    }

    private fun deleteShowRule(seriesId: UUID, alsoDeleteDownloads: Boolean) {
        val show = _state.value.shows.find { it.seriesId == seriesId } ?: return
        viewModelScope.launch {
            if (alsoDeleteDownloads) {
                val userId = repository.getUserId()
                val episodes =
                    withContext(Dispatchers.IO) {
                        database.getEpisodesByShowId(seriesId).map {
                            it.toFindroidEpisode(database, userId)
                        }
                    }
                clearDownloads(episodes, database, downloader)
            }
            show.ruleIds.forEach { ruleRepository.deleteRule(it) }
            loadRules()
        }
    }

    fun onAction(action: AutoDownloadRulesAction) {
        when (action) {
            is AutoDownloadRulesAction.ToggleShowRule ->
                toggleShowRule(action.seriesId, action.enabled)
            is AutoDownloadRulesAction.UpdateShowRule ->
                updateShowRule(
                    action.seriesId,
                    action.seasonIds,
                    action.alsoFutureSeasons,
                    action.onlyNewEpisodes,
                    action.onlyUnwatched,
                )
            is AutoDownloadRulesAction.DeleteShowRule ->
                deleteShowRule(action.seriesId, action.alsoDeleteDownloads)
            is AutoDownloadRulesAction.OnBackClick -> Unit
        }
    }
}
