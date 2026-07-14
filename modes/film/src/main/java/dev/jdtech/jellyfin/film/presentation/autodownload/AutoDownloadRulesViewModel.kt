package dev.jdtech.jellyfin.film.presentation.autodownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.AutoDownloadRuleRepository
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AutoDownloadRulesViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val ruleRepository: AutoDownloadRuleRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(AutoDownloadRulesState())
    val state = _state.asStateFlow()

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
                    rules.mapNotNull { rule ->
                        try {
                            toUiModel(rule)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to resolve auto-download rule ${rule.id}")
                            null
                        }
                    }
                _state.emit(_state.value.copy(isLoading = false, rules = uiModels))
            } catch (e: Exception) {
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }

    private suspend fun toUiModel(rule: AutoDownloadRuleDto): AutoDownloadRuleUiModel {
        val show = repository.getShow(rule.seriesId)
        val seasonLabel =
            rule.seasonId?.let { seasonId ->
                val season = repository.getSeason(seasonId)
                UiText.StringResource(CoreR.string.auto_download_rule_season, season.indexNumber)
            }
        return AutoDownloadRuleUiModel(rule = rule, showName = show.name, seasonLabel = seasonLabel)
    }

    fun onAction(action: AutoDownloadRulesAction) {
        when (action) {
            is AutoDownloadRulesAction.ToggleRule -> {
                viewModelScope.launch {
                    ruleRepository.setRuleEnabled(action.id, action.enabled)
                    loadRules()
                }
            }
            is AutoDownloadRulesAction.ToggleRuleOnlyNewEpisodes -> {
                viewModelScope.launch {
                    ruleRepository.setRuleOnlyNewEpisodes(action.id, action.onlyNewEpisodes)
                    loadRules()
                }
            }
            is AutoDownloadRulesAction.DeleteRule -> {
                viewModelScope.launch {
                    ruleRepository.deleteRule(action.id)
                    loadRules()
                }
            }
            is AutoDownloadRulesAction.OnBackClick -> Unit
        }
    }
}
