package dev.jdtech.jellyfin.film.presentation.autodownload

import dev.jdtech.jellyfin.models.AutoDownloadRuleDto
import dev.jdtech.jellyfin.models.UiText

data class AutoDownloadRuleUiModel(
    val rule: AutoDownloadRuleDto,
    val showName: String,
    // null for show-level rules, "Season N" for season-level rules
    val seasonLabel: UiText?,
)

data class AutoDownloadRulesState(
    val rules: List<AutoDownloadRuleUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
