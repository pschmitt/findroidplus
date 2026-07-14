package dev.jdtech.jellyfin.film.presentation.autodownload

sealed interface AutoDownloadRulesAction {
    data class ToggleRule(val id: Long, val enabled: Boolean) : AutoDownloadRulesAction

    data class ToggleRuleOnlyNewEpisodes(val id: Long, val onlyNewEpisodes: Boolean) :
        AutoDownloadRulesAction

    data class DeleteRule(val id: Long) : AutoDownloadRulesAction

    data object OnBackClick : AutoDownloadRulesAction
}
