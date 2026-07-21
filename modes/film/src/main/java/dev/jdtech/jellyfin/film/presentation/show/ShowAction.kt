package dev.jdtech.jellyfin.film.presentation.show

import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.models.FindroidItem
import java.util.UUID

sealed interface ShowAction {
    data class Play(val startFromBeginning: Boolean = false) : ShowAction

    data class PlayTrailer(val trailer: String) : ShowAction

    data object MarkAsPlayed : ShowAction

    data object UnmarkAsPlayed : ShowAction

    data object MarkAsFavorite : ShowAction

    data object UnmarkAsFavorite : ShowAction

    data class DownloadWithScope(
        val selection: DownloadSelection,
        val alsoFollowNew: Boolean,
        val onlyUnwatched: Boolean,
    ) : ShowAction

    data class DeleteShowDownloads(val alsoRemoveRules: Boolean) : ShowAction

    data object OnBackClick : ShowAction

    data object OnHomeClick : ShowAction

    data object OnSettingsClick : ShowAction

    data class NavigateToItem(val item: FindroidItem) : ShowAction

    data class NavigateToPerson(val personId: UUID) : ShowAction

    /** Opens the Seerr detail view, scoped to [seasonNumber], for a missing-season placeholder
     * card - mirrors SeasonAction.NavigateToSeerr's role for missing-episode rows. */
    data class NavigateToSeerr(val tmdbId: Int, val seasonNumber: Int) : ShowAction
}
