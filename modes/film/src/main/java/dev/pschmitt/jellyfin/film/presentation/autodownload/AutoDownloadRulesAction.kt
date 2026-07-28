package dev.pschmitt.jellyfin.film.presentation.autodownload

import java.util.UUID

sealed interface AutoDownloadRulesAction {
    data class ToggleShowRule(val seriesId: UUID, val enabled: Boolean) : AutoDownloadRulesAction

    data class UpdateShowRule(
        val seriesId: UUID,
        val seasonIds: Set<UUID>,
        val alsoFutureSeasons: Boolean,
        val onlyNewEpisodes: Boolean,
        val onlyUnwatched: Boolean,
        // null = this device (applies to Room as today); non-null = push to that device instead.
        val targetDeviceId: String? = null,
    ) : AutoDownloadRulesAction

    data class DeleteShowRule(val seriesId: UUID, val alsoDeleteDownloads: Boolean) :
        AutoDownloadRulesAction

    data object OnBackClick : AutoDownloadRulesAction
}
