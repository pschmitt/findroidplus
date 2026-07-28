package dev.pschmitt.jellyfin.film.presentation.show

import dev.pschmitt.jellyfin.core.presentation.downloader.DownloadSelection
import dev.pschmitt.jellyfin.models.FindroidItem
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
        // null = this device (applies locally as today); non-null = push to that device instead.
        val targetDeviceId: String? = null,
    ) : ShowAction

    data class DeleteShowDownloads(val alsoRemoveRules: Boolean) : ShowAction

    data class DeleteItem(val cascadeToPvr: Boolean) : ShowAction

    /** Triggers Sonarr's automatic search for every missing episode in the series - there's no
     * manual/interactive counterpart at the series level the way Movie/Episode have, since
     * Sonarr's release picker is per-episode, not per-series. */
    data object SearchSeriesAutomatic : ShowAction

    data object OnBackClick : ShowAction

    data object OnHomeClick : ShowAction

    data object OnSettingsClick : ShowAction

    data class NavigateToItem(val item: FindroidItem) : ShowAction

    data class NavigateToPerson(val personId: UUID) : ShowAction

    /** Opens the Seerr detail view, scoped to [seasonNumber], for a missing-season placeholder
     * card - mirrors SeasonAction.NavigateToSeerr's role for missing-episode rows. */
    data class NavigateToSeerr(val tmdbId: Int, val seasonNumber: Int) : ShowAction

    /** Toggles a "download this season once it's available" request for a missing-season
     * placeholder card - queues it if not already queued, cancels it otherwise. */
    data class ToggleSeasonQueued(val seasonNumber: Int) : ShowAction
}
